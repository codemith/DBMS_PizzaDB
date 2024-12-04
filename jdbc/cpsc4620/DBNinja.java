package cpsc4620;

import java.io.IOException;
import java.sql.*;
import java.util.*;

/*
 * This file is where you will implement the methods needed to support this application.
 * You will write the code to retrieve and save information to the database and use that
 * information to build the various objects required by the applicaiton.
 * 
 * The class has several hard coded static variables used for the connection, you will need to
 * change those to your connection information
 * 
 * This class also has static string variables for pickup, delivery and dine-in. 
 * DO NOT change these constant values.
 * 
 * You can add any helper methods you need, but you must implement all the methods
 * in this class and use them to complete the project.  The autograder will rely on
 * these methods being implemented, so do not delete them or alter their method
 * signatures.
 * 
 * Make sure you properly open and close your DB connections in any method that
 * requires access to the DB.
 * Use the connect_to_db below to open your connection in DBConnector.
 * What is opened must be closed!
 */

/*
 * A utility class to help add and retrieve information from the database
 */

public final class DBNinja {
	private static Connection conn;

	// DO NOT change these variables!
	public final static String pickup = "pickup";
	public final static String delivery = "delivery";
	public final static String dine_in = "dinein";

	public final static String size_s = "Small";
	public final static String size_m = "Medium";
	public final static String size_l = "Large";
	public final static String size_xl = "XLarge";

	public final static String crust_thin = "Thin";
	public final static String crust_orig = "Original";
	public final static String crust_pan = "Pan";
	public final static String crust_gf = "Gluten-Free";

	public enum order_state {
		PREPARED,
		DELIVERED,
		PICKEDUP
	}


	private static boolean connect_to_db() throws SQLException, IOException 
	{

		try {
			conn = DBConnector.make_connection();
			return true;
		} catch (SQLException e) {
			return false;
		} catch (IOException e) {
			return false;
		}

	}

	public static void addOrder(Order o) throws SQLException, IOException
	{
		connect_to_db();

		try {
			connect_to_db();
			if (conn != null) {
				// Insert the order details into the ordertable
				String insertOrderSQL = """
					INSERT INTO ordertable 
					(customer_CustID, ordertable_OrderType, ordertable_OrderDateTime, 
					 ordertable_CustPrice, ordertable_BusPrice, ordertable_IsComplete)
					VALUES (?, ?, ?, ?, ?, ?)
				""";
				PreparedStatement pstmt = conn.prepareStatement(insertOrderSQL, Statement.RETURN_GENERATED_KEYS);
		
				if (o.getCustID() != -1) {
					pstmt.setInt(1, o.getCustID());
				} else {
					pstmt.setObject(1, null);
				}
		
				pstmt.setString(2, o.getOrderType());
				pstmt.setString(3, o.getDate());
				pstmt.setDouble(4, o.getCustPrice());
				pstmt.setDouble(5, o.getBusPrice());
				pstmt.setBoolean(6, o.getIsComplete());
		
				pstmt.executeUpdate();
		
				// Retrieve the generated Order ID
				ResultSet generatedKeys = pstmt.getGeneratedKeys();
				int orderId = (generatedKeys.next()) ? generatedKeys.getInt(1) : throw new SQLException("Order ID generation failed.");
		
				// Handle specific order type details
				handleOrderTypeDetails(o, orderId);
		
				// Process associated pizzas and their details
				for (Pizza pizza : o.getPizzaList()) {
					addPizza(java.sql.Timestamp.valueOf(o.getDate()), orderId, pizza);
				}
		
				// Process associated order discounts
				for (Discount discount : o.getDiscountList()) {
					String discountSQL = "INSERT INTO order_discount (ordertable_OrderID, discount_DiscountID) VALUES (?, ?)";
					try (PreparedStatement discountStmt = conn.prepareStatement(discountSQL)) {
						discountStmt.setInt(1, orderId);
						discountStmt.setInt(2, discount.getDiscountID());
						discountStmt.executeUpdate();
					}
				}
			}
		} finally {
			if (conn != null) {
				conn.close();
			}
		}
		
		/**
		 * Handles order-type-specific details (Delivery, Pickup, or Dine-In).
		 */
		private static void handleOrderTypeDetails(Order o, int orderId) throws SQLException {
			String orderType = o.getOrderType().toLowerCase();
			switch (orderType) {
				case "delivery" -> {
					DeliveryOrder delivery = (DeliveryOrder) o;
					String[] addressParts = delivery.getAddress().split("\t");
					if (addressParts.length != 5) {
						throw new SQLException("Invalid address format: " + delivery.getAddress());
					}
		
					String deliverySQL = """
						INSERT INTO delivery (ordertable_OrderID, delivery_HouseNum, delivery_Street, 
											  delivery_City, delivery_State, delivery_Zip, delivery_IsDelivered)
						VALUES (?, ?, ?, ?, ?, ?, ?)
					""";
					try (PreparedStatement pstmt = conn.prepareStatement(deliverySQL)) {
						pstmt.setInt(1, orderId);
						pstmt.setString(2, addressParts[0]); // House number
						pstmt.setString(3, addressParts[1]); // Street
						pstmt.setString(4, addressParts[2]); // City
						pstmt.setString(5, addressParts[3]); // State
						pstmt.setString(6, addressParts[4]); // Zip code
						pstmt.setBoolean(7, delivery.getIsComplete());
						pstmt.executeUpdate();
					}
				}
				case "pickup" -> {
					String pickupSQL = "INSERT INTO pickup (ordertable_OrderID, pickup_IsPickedUp) VALUES (?, ?)";
					try (PreparedStatement pstmt = conn.prepareStatement(pickupSQL)) {
						pstmt.setInt(1, orderId);
						pstmt.setBoolean(2, false);
						pstmt.executeUpdate();
					}
				}
				case "dinein" -> {
					DineinOrder dineIn = (DineinOrder) o;
					String dineinSQL = "INSERT INTO dinein (ordertable_OrderID, dinein_TableNum) VALUES (?, ?)";
					try (PreparedStatement pstmt = conn.prepareStatement(dineinSQL)) {
						pstmt.setInt(1, orderId);
						pstmt.setInt(2, dineIn.getTableNum());
						pstmt.executeUpdate();
					}
				}
				default -> throw new SQLException("Unknown order type: " + orderType);
			}
		}
	}	


	public static int addPizza(java.util.Date d, int orderID, Pizza p) throws SQLException, IOException {
		/*
		 * Add the code needed to insert the pizza into the database.
		 * Keep in mind you must also add the pizza discounts and toppings
		 * associated with the pizza.
		 *
		 * NOTE: there is a Date object passed into this method so that the Order
		 * and ALL its Pizzas can be assigned the same DTS.
		 *
		 * This method returns the id of the pizza just added.
		 */
	
		int generatedPizzaID = -1; // Initialize pizza ID to track the inserted record
	
		try {
			connect_to_db();
	
			if (conn != null) {
				// Step 1: Insert pizza details
				generatedPizzaID = insertPizzaDetails(orderID, p);
	
				// Step 2: Add toppings associated with the pizza
				addPizzaToppings(generatedPizzaID, p);
	
				// Step 3: Add pizza-specific discounts
				addPizzaDiscounts(generatedPizzaID, p);
			}
		} finally {
			if (conn != null) {
				conn.close();
			}
		}
	
		return generatedPizzaID;
	}
	
	/**
	 * Inserts pizza details into the database and returns the generated Pizza ID.
	 */
	private static int insertPizzaDetails(int orderID, Pizza p) throws SQLException {
		String pizzaSQL = """
			INSERT INTO pizza (ordertable_OrderID, pizza_PizzaState, pizza_PizzaDate, 
							   pizza_CrustType, pizza_Size, pizza_CustPrice, pizza_BusPrice) 
			VALUES (?, ?, ?, ?, ?, ?, ?)
		""";
		try (PreparedStatement pstmt = conn.prepareStatement(pizzaSQL, Statement.RETURN_GENERATED_KEYS)) {
			pstmt.setInt(1, orderID);
			pstmt.setString(2, p.getPizzaState());
			pstmt.setString(3, p.getPizzaDate());
			pstmt.setString(4, p.getCrustType());
			pstmt.setString(5, p.getSize());
			pstmt.setDouble(6, p.getCustPrice());
			pstmt.setDouble(7, p.getBusPrice());
			pstmt.executeUpdate();
	
			ResultSet generatedKeys = pstmt.getGeneratedKeys();
			if (generatedKeys.next()) {
				return generatedKeys.getInt(1); // Return the generated pizza ID
			} else {
				throw new SQLException("Failed to retrieve generated Pizza ID.");
			}
		}
	}
	
	/**
	 * Adds toppings for the pizza in the database and updates the inventory.
	 */
	private static void addPizzaToppings(int pizzaID, Pizza p) throws SQLException {
		String toppingSQL = """
			INSERT INTO pizza_topping (pizza_PizzaID, topping_TopID, pizza_topping_IsDouble) 
			VALUES (?, ?, ?)
		""";
		for (Topping t : p.getToppings()) {
			try (PreparedStatement pstmt = conn.prepareStatement(toppingSQL)) {
				pstmt.setInt(1, pizzaID);
				pstmt.setInt(2, t.getTopID());
				pstmt.setBoolean(3, t.getDoubled());
				pstmt.executeUpdate();
	
				// Update the inventory based on topping usage
				addToInventory(t.getTopID(), t.getDoubled() ? -2 : -1);
			}
		}
	}
	
	/**
	 * Adds discounts associated with the pizza to the database.
	 */
	private static void addPizzaDiscounts(int pizzaID, Pizza p) throws SQLException {
		String discountSQL = "INSERT INTO pizza_discount (pizza_PizzaID, discount_DiscountID) VALUES (?, ?)";
		for (Discount di : p.getDiscounts()) {
			try (PreparedStatement pstmt = conn.prepareStatement(discountSQL)) {
				pstmt.setInt(1, pizzaID);
				pstmt.setInt(2, di.getDiscountID());
				pstmt.executeUpdate();
			}
		}
	}
	
	public static int addCustomer(Customer c) throws SQLException, IOException
	{
		/*
		 * This method adds a new customer to the database.
		 *
		 */
		connect_to_db(); // Connect to the database
		try {
			// Prepare SQL statement for inserting into customer table
			String customerInsertSQL = "INSERT INTO customer (customer_FName, customer_LName, customer_PhoneNum) VALUES (?, ?, ?)";
			PreparedStatement customerInsertStmt = conn.prepareStatement(customerInsertSQL, Statement.RETURN_GENERATED_KEYS);

			// Set the parameters using the Customer object's getters
			customerInsertStmt.setString(1, c.getFName());
			customerInsertStmt.setString(2, c.getLName());
			customerInsertStmt.setString(3, c.getPhone());

			// Execute the insert statement
			int affectedRows = customerInsertStmt.executeUpdate();

			if (affectedRows == 0) {
				throw new SQLException("Creating customer failed, no rows affected.");
			}

			// Retrieve the generated CustID
			ResultSet generatedKeys = customerInsertStmt.getGeneratedKeys();
			int custID;
			if (generatedKeys.next()) {
				custID = generatedKeys.getInt(1);
				c.setCustID(custID); // Update the Customer object with the generated ID
			} else {
				throw new SQLException("Creating customer failed, no ID obtained.");
			}

			// Close the PreparedStatement
			customerInsertStmt.close();

			// Close the connection
			conn.close();

			// Return the CustID
			return custID;

		} catch (SQLException e) {
			// Close the connection in case of exception
			conn.close();
			throw e; // Rethrow the exception
		}
	}


	public static void completeOrder(int OrderID, order_state newState ) throws SQLException, IOException
	{
		/*
		 * Mark that order as complete in the database.
		 * Note: if an order is complete, this means all the pizzas are complete as well.
		 * However, it does not mean that the order has been delivered or picked up!
		 *
		 * For newState = PREPARED: mark the order and all associated pizza's as completed
		 * For newState = DELIVERED: mark the delivery status
		 * FOR newState = PICKEDUP: mark the pickup status
		 *
		 */

		connect_to_db(); // Connect to the database

		try {
			conn.setAutoCommit(false); // Begin transaction

			// Fetch the order type from ordertable
			String queryOrderType = "SELECT ordertable_OrderType FROM ordertable WHERE ordertable_OrderID = ?";
			PreparedStatement stmtOrderType = conn.prepareStatement(queryOrderType);
			stmtOrderType.setInt(1, OrderID);
			ResultSet rsOrderType = stmtOrderType.executeQuery();

			String orderType = null;
			if (rsOrderType.next()) {
				orderType = rsOrderType.getString("ordertable_OrderType");
			} else {
				throw new SQLException("Order ID not found: " + OrderID);
			}

			rsOrderType.close();
			stmtOrderType.close();

			if (newState == order_state.PREPARED) {
				// Update ordertable to mark order as complete
				String updateOrderSQL = "UPDATE ordertable SET ordertable_isComplete = true WHERE ordertable_OrderID = ?";
				PreparedStatement updateOrderStmt = conn.prepareStatement(updateOrderSQL);
				updateOrderStmt.setInt(1, OrderID);
				updateOrderStmt.executeUpdate();
				updateOrderStmt.close();

				// Update pizzas to mark them as complete
				String updatePizzaSQL = "UPDATE pizza SET pizza_PizzaState = 'complete' WHERE ordertable_OrderID = ?";
				PreparedStatement updatePizzaStmt = conn.prepareStatement(updatePizzaSQL);
				updatePizzaStmt.setInt(1, OrderID);
				updatePizzaStmt.executeUpdate();
				updatePizzaStmt.close();
			}
			else if (newState == order_state.DELIVERED) {
				if (orderType.equals(delivery)) {
					// Update delivery table to mark as delivered
					String updateDeliverySQL = "UPDATE delivery SET delivery_IsDelivered = true WHERE ordertable_OrderID = ?";
					PreparedStatement updateDeliveryStmt = conn.prepareStatement(updateDeliverySQL);
					updateDeliveryStmt.setInt(1, OrderID);
					updateDeliveryStmt.executeUpdate();
					updateDeliveryStmt.close();
				} else {
					throw new SQLException("Order ID " + OrderID + " is not a delivery order.");
				}
			}
			else if (newState == order_state.PICKEDUP) {
				if (orderType.equals(pickup)) {
					// Update pickup table to mark as picked up
					String updatePickupSQL = "UPDATE pickup SET pickup_isPickedUp = true WHERE ordertable_OrderID = ?";
					PreparedStatement updatePickupStmt = conn.prepareStatement(updatePickupSQL);
					updatePickupStmt.setInt(1, OrderID);
					updatePickupStmt.executeUpdate();
					updatePickupStmt.close();
				} else {
					throw new SQLException("Order ID " + OrderID + " is not a pickup order.");
				}
			}
			else {
				throw new IllegalArgumentException("Invalid newState value: " + newState);
			}

			// Commit the transaction
			conn.commit();
		} catch (SQLException e) {
			// Rollback transaction in case of error
			conn.rollback();
			throw e;
		} finally {
			// Reset auto-commit and close connection
			conn.setAutoCommit(true);
			conn.close();
		}
	}


	public static ArrayList<Order> getOrders(int status) throws SQLException, IOException {
		connect_to_db(); // Connect to the database
		ArrayList<Order> orders = new ArrayList<>();

		String query = "SELECT * FROM ordertable";
		if (status == 1) {
			query += " WHERE ordertable_isComplete = false";
		} else if (status == 2) {
			query += " WHERE ordertable_isComplete = true";
		} else if (status != 0) {
			throw new IllegalArgumentException("Invalid status value: " + status);
		}

		query += " ORDER BY ordertable_OrderID";

		try (PreparedStatement stmt = conn.prepareStatement(query);
			 ResultSet rs = stmt.executeQuery()) {

			while (rs.next()) {
				int orderID = rs.getInt("ordertable_OrderID");
				int custID = rs.getInt("customer_CustID");
				String orderType = rs.getString("ordertable_OrderType");
				String orderDate = rs.getString("ordertable_OrderDateTime");
				double custPrice = rs.getDouble("ordertable_CustPrice");
				double busPrice = rs.getDouble("ordertable_BusPrice");
				boolean isComplete = rs.getBoolean("ordertable_isComplete");

				// Handle null customer ID
				if (rs.wasNull()) {
					custID = -1;
				}

				Order order = null;

				// Determine the order type and create the appropriate Order object
				if (orderType.equals(dine_in)) {
					String dineInQuery = "SELECT dinein_TableNum FROM dinein WHERE ordertable_OrderID = ?";
					try (PreparedStatement dineInStmt = conn.prepareStatement(dineInQuery)) {
						dineInStmt.setInt(1, orderID);
						try (ResultSet dineInRS = dineInStmt.executeQuery()) {
							int tableNum = 0; // Default table number
							if (dineInRS.next()) {
								tableNum = dineInRS.getInt("dinein_TableNum");
							}
							order = new DineinOrder(orderID, custID, orderDate, custPrice, busPrice, isComplete, tableNum);
						}
					}
				} else if (orderType.equals(pickup)) {
					String pickupQuery = "SELECT pickup_isPickedUp FROM pickup WHERE ordertable_OrderID = ?";
					try (PreparedStatement pickupStmt = conn.prepareStatement(pickupQuery)) {
						pickupStmt.setInt(1, orderID);
						try (ResultSet pickupRS = pickupStmt.executeQuery()) {
							boolean isPickedUp = false;
							if (pickupRS.next()) {
								isPickedUp = pickupRS.getBoolean("pickup_isPickedUp");
							}
							order = new PickupOrder(orderID, custID, orderDate, custPrice, busPrice, isPickedUp, isComplete);
						}
					}
				} else if (orderType.equals(delivery)) {
					String deliveryQuery = "SELECT * FROM delivery WHERE ordertable_OrderID = ?";
					try (PreparedStatement deliveryStmt = conn.prepareStatement(deliveryQuery)) {
						deliveryStmt.setInt(1, orderID);
						try (ResultSet deliveryRS = deliveryStmt.executeQuery()) {
							String houseNum = "", street = "", city = "", state = "", zip = "";
							boolean isDelivered = false;
							if (deliveryRS.next()) {
								houseNum = deliveryRS.getString("delivery_HouseNum");
								street = deliveryRS.getString("delivery_Street");
								city = deliveryRS.getString("delivery_City");
								state = deliveryRS.getString("delivery_State");
								zip = deliveryRS.getString("delivery_Zip");
								isDelivered = deliveryRS.getBoolean("delivery_IsDelivered");
							}
							String address = houseNum + "\t" + street + "\t" + city + "\t" + state + "\t" + zip;
							order = new DeliveryOrder(orderID, custID, orderDate, custPrice, busPrice, isComplete, address, isDelivered);
						}
					}
				}

				if (order != null) {
					// Populate the order with pizzas
					ArrayList<Pizza> pizzas = getPizzas(order);
					order.setPizzaList(pizzas);

					// Populate the order with discounts
					ArrayList<Discount> orderDiscounts = getDiscounts(order);
					order.setDiscountList(orderDiscounts);

					// Add the order to the list
					orders.add(order);
				}
			}
		} finally {
			conn.close(); // Ensure the connection is closed
		}

		return orders;
	}

	public static Order getLastOrder() throws SQLException, IOException
	{
		/*
		 * Query the database for the LAST order added
		 * then return an Order object for that order.
		 * NOTE...there will ALWAYS be a "last order"!
		 */

		connect_to_db(); // Connect to the database

		// Prepare the SQL query to get the last order
		String query = "SELECT * FROM ordertable ORDER BY ordertable_OrderID DESC LIMIT 1";
		Statement stmt = conn.createStatement();
		ResultSet rs = stmt.executeQuery(query);

		Order lastOrder = null;

		if (rs.next()) {
			int orderID = rs.getInt("ordertable_OrderID");
			int custID = rs.getInt("customer_CustID");
			String orderType = rs.getString("ordertable_OrderType");
			String orderDate = rs.getString("ordertable_OrderDateTime");
			double custPrice = rs.getDouble("ordertable_CustPrice");
			double busPrice = rs.getDouble("ordertable_BusPrice");
			boolean isComplete = rs.getBoolean("ordertable_isComplete");

			// Handle null customer ID for dine-in orders
			if (rs.wasNull()) {
				custID = -1;
			}

			// Determine the order type and create the appropriate Order object
			if (orderType.equals(dine_in)) {
				// Dine-in order
				// Fetch dine-in specific data
				String dineInQuery = "SELECT dinein_TableNum FROM dinein WHERE ordertable_OrderID = ?";
				PreparedStatement dineInStmt = conn.prepareStatement(dineInQuery);
				dineInStmt.setInt(1, orderID);
				ResultSet dineInRS = dineInStmt.executeQuery();

				int tableNum = 0;
				if (dineInRS.next()) {
					tableNum = dineInRS.getInt("dinein_TableNum");
				}

				lastOrder = new DineinOrder(orderID, custID, orderDate, custPrice, busPrice, isComplete, tableNum);

				dineInRS.close();
				dineInStmt.close();

			} else if (orderType.equals(delivery)) {
				// Delivery order
				// Fetch delivery specific data
				String deliveryQuery = "SELECT * FROM delivery WHERE ordertable_OrderID = ?";
				PreparedStatement deliveryStmt = conn.prepareStatement(deliveryQuery);
				deliveryStmt.setInt(1, orderID);
				ResultSet deliveryRS = deliveryStmt.executeQuery();

				String houseNum = "";
				String street = "";
				String city = "";
				String state = "";
				String zip = "";
				boolean isDelivered = false;

				if (deliveryRS.next()) {
					houseNum = deliveryRS.getString("delivery_HouseNum");
					street = deliveryRS.getString("delivery_Street");
					city = deliveryRS.getString("delivery_City");
					state = deliveryRS.getString("delivery_State");
					zip = deliveryRS.getString("delivery_Zip");
					isDelivered = deliveryRS.getBoolean("delivery_IsDelivered");
				}

				String address = houseNum + "\t" + street + "\t" + city + "\t" + state + "\t" + zip;

				lastOrder = new DeliveryOrder(orderID, custID, orderDate, custPrice, busPrice, isComplete, address, isDelivered);

				deliveryRS.close();
				deliveryStmt.close();

			} else if (orderType.equals(pickup)) {
				// Pickup order
				// Fetch pickup specific data
				String pickupQuery = "SELECT pickup_isPickedUp FROM pickup WHERE ordertable_OrderID = ?";
				PreparedStatement pickupStmt = conn.prepareStatement(pickupQuery);
				pickupStmt.setInt(1, orderID);
				ResultSet pickupRS = pickupStmt.executeQuery();

				boolean isPickedUp = false;
				if (pickupRS.next()) {
					isPickedUp = pickupRS.getBoolean("pickup_isPickedUp");
				}

				lastOrder = new PickupOrder(orderID, custID, orderDate, custPrice, busPrice, isPickedUp, isComplete);

				pickupRS.close();
				pickupStmt.close();

			} else {
				// Unknown order type
				throw new SQLException("Unknown order type for OrderID: " + orderID);
			}

			// Populate the order with pizzas
			ArrayList<Pizza> pizzas = getPizzas(lastOrder);
			lastOrder.setPizzaList(pizzas);

			// Populate the order with discounts
			ArrayList<Discount> orderDiscounts = getDiscounts(lastOrder);
			lastOrder.setDiscountList(orderDiscounts);
		}

		// Close resources
		rs.close();
		stmt.close();
		conn.close();

		// Return the last order
		return lastOrder;
	}

	public static ArrayList<Order> getOrdersByDate(String date) throws SQLException, IOException
	{
		/*
		 * Query the database for ALL the orders placed on a specific date
		 * and return a list of those orders.
		 *
		 */

		connect_to_db(); // Connect to the database

		ArrayList<Order> orders = new ArrayList<Order>();

		// Prepare the SQL query
		String query = "SELECT * FROM ordertable WHERE DATE(ordertable_OrderDateTime) = ? ORDER BY ordertable_OrderID";
		PreparedStatement stmt = conn.prepareStatement(query);
		stmt.setString(1, date); // Assuming 'date' is in 'YYYY-MM-DD' format

		ResultSet rs = stmt.executeQuery();

		while (rs.next()) {
			int orderID = rs.getInt("ordertable_OrderID");
			int custID = rs.getInt("customer_CustID");
			String orderType = rs.getString("ordertable_OrderType");
			String orderDate = rs.getString("ordertable_OrderDateTime");
			double custPrice = rs.getDouble("ordertable_CustPrice");
			double busPrice = rs.getDouble("ordertable_BusPrice");
			boolean isComplete = rs.getBoolean("ordertable_isComplete");

			// Handle null customer ID for dine-in orders
			if (rs.wasNull()) {
				custID = -1;
			}

			Order order = null;

			// Determine the order type and create the appropriate Order object
			if (orderType.equals(dine_in)) {
				// Dine-in order
				// Fetch dine-in specific data
				String dineInQuery = "SELECT dinein_TableNum FROM dinein WHERE ordertable_OrderID = ?";
				PreparedStatement dineInStmt = conn.prepareStatement(dineInQuery);
				dineInStmt.setInt(1, orderID);
				ResultSet dineInRS = dineInStmt.executeQuery();

				int tableNum = 0;
				if (dineInRS.next()) {
					tableNum = dineInRS.getInt("dinein_TableNum");
				}

				order = new DineinOrder(orderID, custID, orderDate, custPrice, busPrice, isComplete, tableNum);

				dineInRS.close();
				dineInStmt.close();

			} else if (orderType.equals(delivery)) {
				// Delivery order
				// Fetch delivery specific data
				String deliveryQuery = "SELECT * FROM delivery WHERE ordertable_OrderID = ?";
				PreparedStatement deliveryStmt = conn.prepareStatement(deliveryQuery);
				deliveryStmt.setInt(1, orderID);
				ResultSet deliveryRS = deliveryStmt.executeQuery();

				String houseNum = "";
				String street = "";
				String city = "";
				String state = "";
				String zip = "";
				boolean isDelivered = false;

				if (deliveryRS.next()) {
					houseNum = deliveryRS.getString("delivery_HouseNum");
					street = deliveryRS.getString("delivery_Street");
					city = deliveryRS.getString("delivery_City");
					state = deliveryRS.getString("delivery_State");
					zip = deliveryRS.getString("delivery_Zip");
					isDelivered = deliveryRS.getBoolean("delivery_IsDelivered");
				}

				String address = houseNum + "\t" + street + "\t" + city + "\t" + state + "\t" + zip;

				order = new DeliveryOrder(orderID, custID, orderDate, custPrice, busPrice, isComplete, address, isDelivered);

				deliveryRS.close();
				deliveryStmt.close();

			} else if (orderType.equals(pickup)) {
				// Pickup order
				// Fetch pickup specific data
				String pickupQuery = "SELECT pickup_isPickedUp FROM pickup WHERE ordertable_OrderID = ?";
				PreparedStatement pickupStmt = conn.prepareStatement(pickupQuery);
				pickupStmt.setInt(1, orderID);
				ResultSet pickupRS = pickupStmt.executeQuery();

				boolean isPickedUp = false;
				if (pickupRS.next()) {
					isPickedUp = pickupRS.getBoolean("pickup_isPickedUp");
				}

				order = new PickupOrder(orderID, custID, orderDate, custPrice, busPrice, isPickedUp, isComplete);

				pickupRS.close();
				pickupStmt.close();

			} else {
				// Unknown order type
				continue; // Skip to the next order
			}

			// Populate the order with pizzas
			ArrayList<Pizza> pizzas = getPizzas(order);
			order.setPizzaList(pizzas);

			// Populate the order with discounts
			ArrayList<Discount> orderDiscounts = getDiscounts(order);
			order.setDiscountList(orderDiscounts);

			// Add the order to the list
			orders.add(order);
		}

		// Close resources
		rs.close();
		stmt.close();
		conn.close();

		return orders;
	}

	public static ArrayList<Discount> getDiscountList() throws SQLException, IOException
	{
		/*
		 * Query the database for all the available discounts and
		 * return them in an arrayList of discounts ordered by discount name.
		 *
		 */

		connect_to_db(); // Connect to the database

		ArrayList<Discount> discounts = new ArrayList<Discount>();

		String query = "SELECT * FROM discount ORDER BY discount_DiscountName";

		Statement stmt = conn.createStatement();
		ResultSet rs = stmt.executeQuery(query);

		while (rs.next()) {
			int discountID = rs.getInt("discount_DiscountID");
			String discountName = rs.getString("discount_DiscountName");
			double amount = rs.getDouble("discount_Amount");
			boolean isPercent = rs.getBoolean("discount_IsPercent");

			// Create a Discount object
			Discount discount = new Discount(discountID, discountName, amount, isPercent);

			// Add the Discount object to the list
			discounts.add(discount);
		}

		// Close resources
		rs.close();
		stmt.close();
		conn.close();

		// Return the list of discounts
		return discounts;
	}

	public static Discount findDiscountByName(String name) throws SQLException, IOException
	{
		/*
		 * Query the database for a discount using its name.
		 * If found, then return an OrderDiscount object for the discount.
		 * If it's not found....then return null
		 *
		 */

		connect_to_db(); // Connect to the database

		String query = "SELECT * FROM discount WHERE discount_DiscountName = ?";
		PreparedStatement stmt = conn.prepareStatement(query);
		stmt.setString(1, name);

		ResultSet rs = stmt.executeQuery();

		Discount discount = null;

		if (rs.next()) {
			int discountID = rs.getInt("discount_DiscountID");
			String discountName = rs.getString("discount_DiscountName");
			double amount = rs.getDouble("discount_Amount");
			boolean isPercent = rs.getBoolean("discount_IsPercent");

			// Create a Discount object
			discount = new Discount(discountID, discountName, amount, isPercent);
		}

		// Close resources
		rs.close();
		stmt.close();
		conn.close();

		// Return the discount (could be null if not found)
		return discount;
	}

	public static ArrayList<Customer> getCustomerList() throws SQLException, IOException
	{
		/*
		 * Query the data for all the customers and return an arrayList of all the customers.
		 * Don't forget to order the data coming from the database appropriately.
		 *
		 */

		connect_to_db(); // Connect to the database

		ArrayList<Customer> customers = new ArrayList<Customer>();

		String query = "SELECT * FROM customer ORDER BY customer_LName, customer_FName";

		Statement stmt = conn.createStatement();
		ResultSet rs = stmt.executeQuery(query);

		while (rs.next()) {
			int custID = rs.getInt("customer_CustID");
			String fName = rs.getString("customer_FName");
			String lName = rs.getString("customer_LName");
			String phone = rs.getString("customer_PhoneNum");

			// Create a Customer object
			Customer customer = new Customer(custID, fName, lName, phone);

			// Add the Customer object to the list
			customers.add(customer);
		}

		// Close resources
		rs.close();
		stmt.close();
		conn.close();

		// Return the list of customers
		return customers;
	}

	public static Customer findCustomerByPhone(String phoneNumber)  throws SQLException, IOException
	{
		/*
		 * Query the database for a customer using a phone number.
		 * If found, then return a Customer object for the customer.
		 * If it's not found....then return null
		 *
		 */

		connect_to_db(); // Connect to the database

		String query = "SELECT * FROM customer WHERE customer_PhoneNum = ?";
		PreparedStatement stmt = conn.prepareStatement(query);
		stmt.setString(1, phoneNumber);

		ResultSet rs = stmt.executeQuery();

		Customer customer = null;

		if (rs.next()) {
			int custID = rs.getInt("customer_CustID");
			String fName = rs.getString("customer_FName");
			String lName = rs.getString("customer_LName");
			String phone = rs.getString("customer_PhoneNum");

			// Create a Customer object
			customer = new Customer(custID, fName, lName, phone);
		}

		// Close resources
		rs.close();
		stmt.close();
		conn.close();

		// Return the customer (could be null if not found)
		return customer;
	}

	public static String getCustomerName(int CustID) throws SQLException, IOException
	{
		/*
		 * COMPLETED...WORKING Example!
		 *
		 * This is a helper method to fetch and format the name of a customer
		 * based on a customer ID. This is an example of how to interact with
		 * your database from Java.
		 *
		 * Notice how the connection to the DB made at the start of the
		 *
		 */

		connect_to_db();

		/*
		 * an example query using a constructed string...
		 * remember, this style of query construction could be subject to SQL injection attacks!
		 *
		 */
		String cname1 = "";
		String cname2 = "";
		String query = "Select customer_FName, customer_LName From customer WHERE customer_CustID=" + CustID + ";";
		Statement stmt = conn.createStatement();
		ResultSet rset = stmt.executeQuery(query);

		while(rset.next())
		{
			cname1 = rset.getString(1) + " " + rset.getString(2);
		}

		/*
		 * a BETTER example of the same query using a prepared statement...
		 * with exception handling
		 *
		 */
		try {
			PreparedStatement os;
			ResultSet rset2;
			String query2;
			query2 = "Select customer_FName, customer_LName From customer WHERE customer_CustID=?;";
			os = conn.prepareStatement(query2);
			os.setInt(1, CustID);
			rset2 = os.executeQuery();
			while(rset2.next())
			{
				cname2 = rset2.getString("customer_FName") + " " + rset2.getString("customer_LName"); // note the use of field names in the getString methods
			}
		} catch (SQLException e) {
			e.printStackTrace();
			// process the error or re-raise the exception to a higher level
		}

		conn.close();

		return cname1;
		// OR
		// return cname2;

	}



	public static ArrayList<Topping> getToppingList() throws SQLException, IOException
	{
		/*
		 * Query the database for the available toppings and
		 * return an arrayList of all the available toppings.
		 * Don't forget to order the data coming from the database appropriately.
		 *
		 */

		connect_to_db(); // Connect to the database

		ArrayList<Topping> toppings = new ArrayList<Topping>();

		String query = "SELECT * FROM topping ORDER BY topping_TopName";

		Statement stmt = conn.createStatement();
		ResultSet rs = stmt.executeQuery(query);

		while (rs.next()) {
			int topID = rs.getInt("topping_TopID");
			String topName = rs.getString("topping_TopName");
			double smallAMT = rs.getDouble("topping_SmallAMT");
			double medAMT = rs.getDouble("topping_MedAMT");
			double lgAMT = rs.getDouble("topping_LgAMT");
			double xlAMT = rs.getDouble("topping_XLAMT");
			double custPrice = rs.getDouble("topping_CustPrice");
			double busPrice = rs.getDouble("topping_BusPrice");
			int minINVT = rs.getInt("topping_MinINVT");
			int curINVT = rs.getInt("topping_CurINVT");

			// Create a Topping object
			Topping topping = new Topping(topID, topName, smallAMT, medAMT, lgAMT, xlAMT, custPrice, busPrice, minINVT, curINVT);

			// Add the Topping object to the list
			toppings.add(topping);
		}

		// Close resources
		rs.close();
		stmt.close();
		conn.close();

		// Return the list of toppings
		return toppings;
	}


	public static Topping findToppingByName(String name) throws SQLException, IOException
	{
		/*
		 * Query the database for the topping using it's name.
		 * If found, then return a Topping object for the topping.
		 * If it's not found....then return null
		 *
		 */

		connect_to_db(); // Connect to the database

		String query = "SELECT * FROM topping WHERE topping_TopName = ?";
		PreparedStatement stmt = conn.prepareStatement(query);
		stmt.setString(1, name);

		ResultSet rs = stmt.executeQuery();

		Topping topping = null;

		if (rs.next()) {
			int topID = rs.getInt("topping_TopID");
			String topName = rs.getString("topping_TopName");
			double smallAMT = rs.getDouble("topping_SmallAMT");
			double medAMT = rs.getDouble("topping_MedAMT");
			double lgAMT = rs.getDouble("topping_LgAMT");
			double xlAMT = rs.getDouble("topping_XLAMT");
			double custPrice = rs.getDouble("topping_CustPrice");
			double busPrice = rs.getDouble("topping_BusPrice");
			int minINVT = rs.getInt("topping_MinINVT");
			int curINVT = rs.getInt("topping_CurINVT");

			// Create a Topping object
			topping = new Topping(topID, topName, smallAMT, medAMT, lgAMT, xlAMT, custPrice, busPrice, minINVT, curINVT);
		}

		// Close resources
		rs.close();
		stmt.close();
		conn.close();

		// Return the topping (could be null if not found)
		return topping;
	}


	public static ArrayList<Topping> getToppingsOnPizza(Pizza p) throws SQLException, IOException
	{
		/*
		 * This method builds an ArrayList of the toppings ON a pizza.
		 * The list can then be added to the Pizza object elsewhere in the
		 */

		connect_to_db(); // Connect to the database

		ArrayList<Topping> toppings = new ArrayList<Topping>();

		String query = "SELECT t.*, pt.pizza_topping_IsDouble " +
				"FROM topping t " +
				"JOIN pizza_topping pt ON t.topping_TopID = pt.topping_TopID " +
				"WHERE pt.pizza_PizzaID = ? " +
				"ORDER BY t.topping_TopName";

		PreparedStatement stmt = conn.prepareStatement(query);
		stmt.setInt(1, p.getPizzaID()); // Set the pizza ID parameter

		ResultSet rs = stmt.executeQuery();

		while (rs.next()) {
			int topID = rs.getInt("topping_TopID");
			String topName = rs.getString("topping_TopName");
			double smallAMT = rs.getDouble("topping_SmallAMT");
			double medAMT = rs.getDouble("topping_MedAMT");
			double lgAMT = rs.getDouble("topping_LgAMT");
			double xlAMT = rs.getDouble("topping_XLAMT");
			double custPrice = rs.getDouble("topping_CustPrice");
			double busPrice = rs.getDouble("topping_BusPrice");
			int minINVT = rs.getInt("topping_MinINVT");
			int curINVT = rs.getInt("topping_CurINVT");
			boolean isDoubled = rs.getBoolean("pizza_topping_IsDouble");

			// Create a Topping object
			Topping topping = new Topping(topID, topName, smallAMT, medAMT, lgAMT, xlAMT, custPrice, busPrice, minINVT, curINVT);
			topping.setDoubled(isDoubled); // Set whether the topping is doubled on this pizza

			// Add the Topping object to the list
			toppings.add(topping);
		}

		// Close resources
		rs.close();
		stmt.close();
		conn.close();

		// Return the list of toppings
		return toppings;
	}


	public static void addToInventory(int toppingID, double quantity) throws SQLException, IOException
	{
		/*
		 * Updates the quantity of the topping in the database by the amount specified.
		 *
		 */

		connect_to_db(); // Connect to the database

		String updateQuery = "UPDATE topping SET topping_CurINVT = topping_CurINVT + ? WHERE topping_TopID = ?";

		PreparedStatement stmt = conn.prepareStatement(updateQuery);
		stmt.setDouble(1, quantity);   // Set the quantity to add
		stmt.setInt(2, toppingID);     // Set the topping ID

		int rowsAffected = stmt.executeUpdate();

		if (rowsAffected == 0) {
			// No rows updated, possibly invalid toppingID
			System.out.println("No topping found with TopID: " + toppingID);
		} else {
			System.out.println("Inventory updated successfully for TopID: " + toppingID);
		}

		// Close resources
		stmt.close();
		conn.close();
	}



	public static ArrayList<Pizza> getPizzas(Order o) throws SQLException, IOException
	{
		/*
		 * Build an ArrayList of all the Pizzas associated with the Order.
		 *
		 */

		connect_to_db(); // Connect to the database

		ArrayList<Pizza> pizzas = new ArrayList<Pizza>();

		String query = "SELECT * FROM pizza WHERE ordertable_OrderID = ? ORDER BY pizza_PizzaID";

		PreparedStatement stmt = conn.prepareStatement(query);
		stmt.setInt(1, o.getOrderID()); // Set the order ID parameter

		ResultSet rs = stmt.executeQuery();

		while (rs.next()) {
			int pizzaID = rs.getInt("pizza_PizzaID");
			String size = rs.getString("pizza_Size");
			String crustType = rs.getString("pizza_CrustType");
			String pizzaState = rs.getString("pizza_PizzaState");
			String pizzaDate = rs.getString("pizza_PizzaDate");
			double custPrice = rs.getDouble("pizza_CustPrice");
			double busPrice = rs.getDouble("pizza_BusPrice");
			int orderID = rs.getInt("ordertable_OrderID");

			// Create a Pizza object
			Pizza pizza = new Pizza(pizzaID, size, crustType, orderID, pizzaState, pizzaDate, custPrice, busPrice);

			// Populate toppings
			ArrayList<Topping> toppings = getToppingsOnPizza(pizza);
			pizza.setToppings(toppings);

			// Populate discounts
			ArrayList<Discount> pizzaDiscounts = getDiscounts(pizza);
			pizza.setDiscounts(pizzaDiscounts);

			// Add pizza to the list
			pizzas.add(pizza);
		}

		// Close resources
		rs.close();
		stmt.close();
		conn.close();

		// Return the list of pizzas
		return pizzas;
	}


	public static ArrayList<Discount> getDiscounts(Order o) throws SQLException, IOException
	{
		/*
		 * Build an array list of all the Discounts associated with the Order.
		 *
		 */

		connect_to_db(); // Connect to the database

		ArrayList<Discount> discounts = new ArrayList<Discount>();

		String query = "SELECT d.* " +
				"FROM discount d " +
				"JOIN order_discount od ON d.discount_DiscountID = od.discount_DiscountID " +
				"WHERE od.ordertable_OrderID = ? " +
				"ORDER BY d.discount_DiscountName";

		PreparedStatement stmt = conn.prepareStatement(query);
		stmt.setInt(1, o.getOrderID()); // Set the order ID parameter

		ResultSet rs = stmt.executeQuery();

		while (rs.next()) {
			int discountID = rs.getInt("discount_DiscountID");
			String discountName = rs.getString("discount_DiscountName");
			double amount = rs.getDouble("discount_Amount");
			boolean isPercent = rs.getBoolean("discount_IsPercent");

			// Create a Discount object
			Discount discount = new Discount(discountID, discountName, amount, isPercent);

			// Add the Discount object to the list
			discounts.add(discount);
		}

		// Close resources
		rs.close();
		stmt.close();
		conn.close();

		// Return the list of discounts
		return discounts;
	}


	public static ArrayList<Discount> getDiscounts(Pizza p) throws SQLException, IOException
	{
		/*
		 * Build an array list of all the Discounts associated with the Pizza.
		 *
		 */

		connect_to_db(); // Connect to the database

		ArrayList<Discount> discounts = new ArrayList<Discount>();

		String query = "SELECT d.* " +
				"FROM discount d " +
				"JOIN pizza_discount pd ON d.discount_DiscountID = pd.discount_DiscountID " +
				"WHERE pd.pizza_PizzaID = ? " +
				"ORDER BY d.discount_DiscountName";

		PreparedStatement stmt = conn.prepareStatement(query);
		stmt.setInt(1, p.getPizzaID()); // Set the pizza ID parameter

		ResultSet rs = stmt.executeQuery();

		while (rs.next()) {
			int discountID = rs.getInt("discount_DiscountID");
			String discountName = rs.getString("discount_DiscountName");
			double amount = rs.getDouble("discount_Amount");
			boolean isPercent = rs.getBoolean("discount_IsPercent");

			// Create a Discount object
			Discount discount = new Discount(discountID, discountName, amount, isPercent);

			// Add the Discount object to the list
			discounts.add(discount);
		}

		// Close resources
		rs.close();
		stmt.close();
		conn.close();

		// Return the list of discounts
		return discounts;
	}

	public static double getBaseCustPrice(String size, String crust) throws SQLException, IOException
	{
		/*
		 * Query the database for the base customer price for that size and crust pizza.
		 *
		 */

		connect_to_db(); // Connect to the database

		double basePrice = 0.0;

		String query = "SELECT baseprice_CustPrice FROM baseprice WHERE baseprice_Size = ? AND baseprice_CrustType = ?";

		PreparedStatement stmt = conn.prepareStatement(query);
		stmt.setString(1, size);   // Set the size parameter
		stmt.setString(2, crust);  // Set the crust type parameter

		ResultSet rs = stmt.executeQuery();

		if (rs.next()) {
			basePrice = rs.getDouble("baseprice_CustPrice");
		} else {
			// No matching record found, handle accordingly
			System.out.println("No base price found for Size: " + size + ", Crust: " + crust);
		}

		// Close resources
		rs.close();
		stmt.close();
		conn.close();

		// Return the base customer price
		return basePrice;
	}


	public static double getBaseBusPrice(String size, String crust) throws SQLException, IOException
	{
		/*
		 * Query the database for the base business price for that size and crust pizza.
		 *
		 */

		connect_to_db(); // Connect to the database

		double basePrice = 0.0;

		String query = "SELECT baseprice_BusPrice FROM baseprice WHERE baseprice_Size = ? AND baseprice_CrustType = ?";

		PreparedStatement stmt = conn.prepareStatement(query);
		stmt.setString(1, size);   // Set the size parameter
		stmt.setString(2, crust);  // Set the crust type parameter

		ResultSet rs = stmt.executeQuery();

		if (rs.next()) {
			basePrice = rs.getDouble("baseprice_BusPrice");
		} else {
			// No matching record found, handle accordingly
			System.out.println("No base business price found for Size: " + size + ", Crust: " + crust);
		}

		// Close resources
		rs.close();
		stmt.close();
		conn.close();

		// Return the base business price
		return basePrice;
	}

	public static void printToppingPopReport() throws SQLException, IOException
	{
		/*
		 * Prints the ToppingPopularity view. Remember that this view
		 * needs to exist in your DB, so be sure you've run your createViews.sql
		 * files on your testing DB if you haven't already.
		 *
		 * The result should be readable and sorted as indicated in the prompt.
		 *
		 * HINT: You need to match the expected output EXACTLY....I would suggest
		 * you look at the printf method (rather that the simple print of println).
		 * It operates the same in Java as it does in C and will make your code
		 * better.
		 *
		 */

		connect_to_db(); // Connect to the database

		String query = "SELECT * FROM ToppingPopularity ORDER BY topping_TopName";

		Statement stmt = conn.createStatement();
		ResultSet rs = stmt.executeQuery(query);

		// Print the header
		System.out.printf("%-25s %-15s %-15s%n", "Topping Name", "Total Pizzas", "Total Quantity");
		System.out.println("---------------------------------------------------------------");

		// Iterate over the results
		while (rs.next()) {
			String toppingName = rs.getString("topping_TopName");
			int totalPizzas = rs.getInt("TotalPizzas");
			double totalQuantity = rs.getDouble("TotalQuantity");

			System.out.printf("%-25s %-15d %-15.2f%n", toppingName, totalPizzas, totalQuantity);
		}

		// Close resources
		rs.close();
		stmt.close();
		conn.close();
	}
	public static void printProfitByPizzaReport() throws SQLException, IOException {
		/*
		 * Prints the ProfitByPizza view. Remember that this view
		 * needs to exist in your DB, so be sure you've run your createViews.sql
		 * files on your testing DB if you haven't already.
		 *
		 * The result should be readable and sorted as indicated in the prompt.
		 *
		 * HINT: You need to match the expected output EXACTLY....I would suggest
		 * you look at the printf method (rather than the simple print of println).
		 * It operates the same in Java as it does in C and will make your code
		 * better.
		 */

		connect_to_db(); // Connect to the database

		String query = "SELECT * FROM ProfitByPizza ORDER BY Profit DESC";

		Statement stmt = conn.createStatement();
		ResultSet rs = stmt.executeQuery(query);

		// Print the header
		System.out.printf("%-10s %-15s %-15s %-15s %-15s%n", "Size", "Crust Type", "Order Month", "Total Pizzas", "Total Profit");
		System.out.println("--------------------------------------------------------------------------");

		// Iterate over the results
		while (rs.next()) {
			String size = rs.getString("Size");
			String crustType = rs.getString("Crust");
			String orderMonth = rs.getString("OrderMonth");
			int totalPizzas = rs.getInt("TotalPizzas");
			double profit = rs.getDouble("Profit");

			System.out.printf("%-10s %-15s %-15s %-15d $%-14.2f%n", size, crustType, orderMonth, totalPizzas, profit);
		}

		// Close resources
		rs.close();
		stmt.close();
		conn.close();
	}

	public static void printProfitByOrderType() throws SQLException, IOException
	{
		/*
		 * Prints the ProfitByOrderType view. Remember that this view
		 * needs to exist in your DB, so be sure you've run your createViews.sql
		 * files on your testing DB if you haven't already.
		 *
		 * The result should be readable and sorted as indicated in the prompt.
		 *
		 * HINT: You need to match the expected output EXACTLY....I would suggest
		 * you look at the printf method (rather than the simple print or println).
		 * It operates the same in Java as it does in C and will make your code
		 * better.
		 *
		 */

		connect_to_db(); // Connect to the database

		String query = "SELECT * FROM ProfitByOrderType ORDER BY Profit DESC";

		Statement stmt = conn.createStatement();
		ResultSet rs = stmt.executeQuery(query);

		// Print the header
		System.out.printf("%-15s %-15s %-15s%n", "Order Type", "Total Orders", "Total Profit");
		System.out.println("-----------------------------------------------");

		// Iterate over the results
		while (rs.next()) {
			String orderType = rs.getString("OrderType");
			int totalOrders = rs.getInt("TotalOrders");
			double Profit = rs.getDouble("Profit");

			System.out.printf("%-15s %-15d $%-14.2f%n", orderType, totalOrders, Profit);
		}

		// Close resources
		rs.close();
		stmt.close();
		conn.close();
	}

	
	
	/*
	 * These private methods help get the individual components of an SQL datetime object. 
	 * You're welcome to keep them or remove them....but they are usefull!
	 */
	private static int getYear(String date)// assumes date format 'YYYY-MM-DD HH:mm:ss'
	{
		return Integer.parseInt(date.substring(0,4));
	}
	private static int getMonth(String date)// assumes date format 'YYYY-MM-DD HH:mm:ss'
	{
		return Integer.parseInt(date.substring(5, 7));
	}
	private static int getDay(String date)// assumes date format 'YYYY-MM-DD HH:mm:ss'
	{
		return Integer.parseInt(date.substring(8, 10));
	}

	public static boolean checkDate(int year, int month, int day, String dateOfOrder)
	{
		if(getYear(dateOfOrder) > year)
			return true;
		else if(getYear(dateOfOrder) < year)
			return false;
		else
		{
			if(getMonth(dateOfOrder) > month)
				return true;
			else if(getMonth(dateOfOrder) < month)
				return false;
			else
			{
				if(getDay(dateOfOrder) >= day)
					return true;
				else
					return false;
			}
		}
	}


}