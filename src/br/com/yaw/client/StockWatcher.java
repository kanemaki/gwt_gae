package br.com.yaw.client;

import java.util.ArrayList;
import java.util.Date;

import com.google.gwt.core.client.EntryPoint;
import com.google.gwt.core.client.GWT;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.event.dom.client.KeyPressEvent;
import com.google.gwt.event.dom.client.KeyPressHandler;
import com.google.gwt.i18n.client.DateTimeFormat;
import com.google.gwt.i18n.client.NumberFormat;
import com.google.gwt.user.client.Random;
import com.google.gwt.user.client.Timer;
import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.FlexTable;
import com.google.gwt.user.client.ui.HorizontalPanel;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.RootPanel;
import com.google.gwt.user.client.ui.TextBox;
import com.google.gwt.user.client.ui.VerticalPanel;

public class StockWatcher implements EntryPoint {

  private static final int REFRESH_INTERVAL = 5000; // ms
  private VerticalPanel mainPanel = new VerticalPanel();
  private FlexTable stocksFlexTable = new FlexTable();
  private HorizontalPanel addPanel = new HorizontalPanel();
  private TextBox newSymbolTextBox = new TextBox();
  private Button addStockButton = new Button("Add");
  private Label lastUpdatedLabel = new Label();
  private ArrayList<String> stocks = new ArrayList<String>();
  private StockWatcherConstants constants = GWT.create(StockWatcherConstants.class);
  private StockWatcherMessages messages = GWT.create(StockWatcherMessages.class);
  private StockPriceServiceAsync stockPriceSvc = GWT.create(StockPriceService.class);
  private Label errorMsgLabel = new Label();

  /**
   * Entry point method.
   */
  public void onModuleLoad() {
	  
	// Set the window title, the header text, and the Add button text.
	    Window.setTitle(constants.stockWatcher());
	    RootPanel.get("appTitle").add(new Label(constants.stockWatcher()));
	    addStockButton = new Button(constants.add());
	    
    // Create table for stock data.
	    stocksFlexTable.setText(0, 0, constants.symbol());
	    stocksFlexTable.setText(0, 1, constants.price());
	    stocksFlexTable.setText(0, 2, constants.change());
	    stocksFlexTable.setText(0, 3, constants.remove());
    
    stocksFlexTable.setCellPadding(6);
    
    // Add styles to elements in the stock list table.
    stocksFlexTable.getRowFormatter().addStyleName(0, "watchListHeader");
    stocksFlexTable.addStyleName("watchList");
    stocksFlexTable.getCellFormatter().addStyleName(0, 1, "watchListNumericColumn");
    stocksFlexTable.getCellFormatter().addStyleName(0, 2, "watchListNumericColumn");
    stocksFlexTable.getCellFormatter().addStyleName(0, 3, "watchListRemoveColumn");

    // Assemble Add Stock panel.
    addPanel.add(newSymbolTextBox);
    addPanel.add(addStockButton);
    addPanel.addStyleName("addPanel");
    errorMsgLabel.setStyleName("errorMessage");
    errorMsgLabel.setVisible(false);
    
    // Assemble Main panel.
    mainPanel.add(errorMsgLabel);
    mainPanel.add(stocksFlexTable);
    mainPanel.add(addPanel);
    mainPanel.add(lastUpdatedLabel);

    // Associate the Main panel with the HTML host page.
    RootPanel.get("stock_list").add(mainPanel);

    // Move cursor focus to the input box.
    newSymbolTextBox.setFocus(true);
    
 // Setup timer to refresh list automatically.
    Timer refreshTimer = new Timer() {
      @Override
      public void run() {
        refreshWatchList();
      }
    };
    refreshTimer.scheduleRepeating(REFRESH_INTERVAL);
    
    //Listen for mouse events on Add Button
    addStockButton.addClickHandler(new AddClickHancler());
    newSymbolTextBox.addKeyPressHandler(new NewSymbolTextBoxHandle());
  }
  
  private void refreshWatchList() {
	  // Initialize the service proxy.
	    if (stockPriceSvc == null) {
	      stockPriceSvc = GWT.create(StockPriceService.class);
	    }

	    // Set up the callback object.
	    AsyncCallback<StockPrice[]> callback = new AsyncCallback<StockPrice[]>() {
	      public void onFailure(Throwable caught) {
	    	// If the stock code is in the list of delisted codes, display an error message.
	          String details = caught.getMessage();
	          if (caught instanceof DelistedException) {
	            details = "Company '" + ((DelistedException)caught).getSymbol() + "' was delisted";
	          }

	          errorMsgLabel.setText("Error: " + details);
	          errorMsgLabel.setVisible(true);
	      }

	      public void onSuccess(StockPrice[] result) {
	        updateTable(result);
	      }
	    };

	    // Make the call to the stock price service.
	    stockPriceSvc.getPrices(stocks.toArray(new String[0]), callback);

  }
  
  /**
   * Update a single row in the stock table.
   *
   * @param price Stock data for a single row.
   */
  private void updateTable(StockPrice price) {
	// Make sure the stock is still in the stock table.
	    if (!stocks.contains(price.getSymbol())) {
	      return;
	    }

	    int row = stocks.indexOf(price.getSymbol()) + 1;

	    // Format the data in the Price and Change fields.
	    String priceText = NumberFormat.getFormat("#,##0.00").format(
	        price.getPrice());
	    NumberFormat changeFormat = NumberFormat.getFormat("+#,##0.00;-#,##0.00");
	    String changeText = changeFormat.format(price.getChange());
	    String changePercentText = changeFormat.format(price.getChangePercent());

	    // Populate the Price and Change fields with new data.
	    stocksFlexTable.setText(row, 1, priceText);
	    Label changeWidget = (Label)stocksFlexTable.getWidget(row, 2);
	    changeWidget.setText(changeText + " (" + changePercentText + "%)");
	    
	 // Change the color of text in the Change field based on its value.
	    String changeStyleName = "noChange";
	    if (price.getChangePercent() < -0.1f) {
	      changeStyleName = "negativeChange";
	    }
	    else if (price.getChangePercent() > 0.1f) {
	      changeStyleName = "positiveChange";
	    }

	    changeWidget.setStyleName(changeStyleName);
  }
  
  private void updateTable(StockPrice[] prices) {
	  for (int i = 0; i < prices.length; i++) {
	      updateTable(prices[i]);
	   }
	// Display timestamp showing last refresh.
	  lastUpdatedLabel.setText(messages.lastUpdate(new Date()));
	  
	// Clear any errors.
	    errorMsgLabel.setVisible(false);
  }
  
  public void addStock() {
	  final String symbol = newSymbolTextBox.getText().toUpperCase().trim();
	    newSymbolTextBox.setFocus(true);

	    // Stock code must be between 1 and 10 chars that are numbers, letters, or dots.
	    if (!symbol.matches("^[0-9A-Z\\.]{1,10}$")) {
	    	Window.alert(messages.invalidSymbol(symbol));
	      newSymbolTextBox.selectAll();
	      return;
	    }
	    
	 // Don't add the stock if it's already in the table.
	    if (stocks.contains(symbol))
	      return;
	    
	    // Add the stock to the table.
	    int row = stocksFlexTable.getRowCount();
	    stocks.add(symbol);
	    stocksFlexTable.setText(row, 0, symbol);
	    stocksFlexTable.setWidget(row, 2, new Label());
	    stocksFlexTable.getCellFormatter().addStyleName(row, 1, "watchListNumericColumn");
	    stocksFlexTable.getCellFormatter().addStyleName(row, 2, "watchListNumericColumn");
	    stocksFlexTable.getCellFormatter().addStyleName(row, 3, "watchListRemoveColumn");
	    
	    newSymbolTextBox.setText("");
	    
	    Button removeStockButton = new Button("x");
	    removeStockButton.addClickHandler(new ClickHandler() {
	      public void onClick(ClickEvent event) {
	        int removedIndex = stocks.indexOf(symbol);
	        stocks.remove(removedIndex);
	        stocksFlexTable.removeRow(removedIndex + 1);
	      }
	    });
	    stocksFlexTable.setWidget(row, 3, removeStockButton);
  }
  
  
  /*  ***************** Classes internas tratadoras de eventos ******************/
  
  
  class AddClickHancler implements ClickHandler{

	@Override
	public void onClick(ClickEvent event) {
		addStock();
		
	}
	   
  }
  
  class NewSymbolTextBoxHandle implements KeyPressHandler{

	@Override
	public void onKeyPress(KeyPressEvent event) {
		if(event.getCharCode() == KeyCodes.KEY_ENTER)
			addStock();
	}
	  
  }

}