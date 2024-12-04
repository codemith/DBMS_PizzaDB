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
			conn.setAutoCommit(false);

			// Insert into ordertable
			String orderInsertSQL = "INSERT INTO ordertable (customer_CustID, ordertable_OrderType, ordertable_OrderDateTime, ordertable_CustPrice, ordertable_BusPrice, ordertable_isComplete) VALUES (?, ?, ?, ?, ?, ?)";
			PreparedStatement orderInsertStmt = conn.prepareStatement(orderInsertSQL, Statement.RETURN_GENERATED_KEYS);

			if(o.getCustID() == -1) {
				orderInsertStmt.setNull(1, java.sql.Types.INTEGER);
			} else {
				orderInsertStmt.setInt(1, o.getCustID());
			}

			orderInsertStmt.setString(2, o.getOrderType());
			orderInsertStmt.setString(3, o.getDate());
			orderInsertStmt.setDouble(4, o.getCustPrice());
			orderInsertStmt.setDouble(5, o.getBusPrice());
			orderInsertStmt.setBoolean(6, o.getIsComplete());

			int affectedRows = orderInsertStmt.executeUpdate();

			if(affectedRows == 0) {
				throw new SQLException("Creating order failed, no rows affected.");
			}

			ResultSet generatedKeys = orderInsertStmt.getGeneratedKeys();
			int orderID;
			if(generatedKeys.next()) {
				orderID = generatedKeys.getInt(1);
				o.setOrderID(orderID);
			} else {
				throw new SQLException("Creating order failed, no ID obtained.");
			}

			// Insert into specific order type table
			if(o.getOrderType().equals(dine_in)) {
				DineinOrder dineinOrder = (DineinOrder)o;
				String dineinInsertSQL = "INSERT INTO dinein (ordertable_OrderID, dinein_TableNum) VALUES (?, ?)";
				PreparedStatement dineinInsertStmt = conn.prepareStatement(dineinInsertSQL);
				dineinInsertStmt.setInt(1, orderID);
				dineinInsertStmt.setInt(2, dineinOrder.getTableNum());
				dineinInsertStmt.executeUpdate();
			} else if(o.getOrderType().equals(pickup)) {
				PickupOrder pickupOrder = (PickupOrder)o;
				String pickupInsertSQL = "INSERT INTO pickup (ordertable_OrderID, pickup_isPickedUp) VALUES (?, ?)";
				PreparedStatement pickupInsertStmt = conn.prepareStatement(pickupInsertSQL);
				pickupInsertStmt.setInt(1, orderID);
				pickupInsertStmt.setBoolean(2, pickupOrder.getIsPickedUp());
				pickupInsertStmt.executeUpdate();
			} else if(o.getOrderType().equals(delivery)) {
				DeliveryOrder deliveryOrder = (DeliveryOrder)o;
				String[] addressParts = deliveryOrder.getAddress().split("\\t");
				String houseNum = addressParts[0];
				String street = addressParts[1];
				String city = addressParts[2];
				String state = addressParts[3];
				String zip = addressParts[4];

				String deliveryInsertSQL = "INSERT INTO delivery (ordertable_OrderID, delivery_HouseNum, delivery_Street, delivery_City, delivery_State, delivery_Zip, delivery_IsDelivered) VALUES (?, ?, ?, ?, ?, ?, ?)";
				PreparedStatement deliveryInsertStmt = conn.prepareStatement(deliveryInsertSQL);
				deliveryInsertStmt.setInt(1, orderID);
				deliveryInsertStmt.setString(2, houseNum);
				deliveryInsertStmt.setString(3, street);
				deliveryInsertStmt.setString(4, city);
				deliveryInsertStmt.setString(5, state);
				deliveryInsertStmt.setString(6, zip);
				deliveryInsertStmt.setBoolean(7, deliveryOrder.getIsComplete());
				deliveryInsertStmt.executeUpdate();
			} else {
				throw new SQLException("Invalid OrderType");
			}

			// Insert pizzas and their toppings
			for(Pizza p : o.getPizzaList()) {
				// We can use the addPizza method here
				addPizza(java.sql.Timestamp.valueOf(o.getDate()), orderID, p);
			}

			// Insert order discounts
			for(Discount d : o.getDiscountList()) {
				try {
					connect_to_db();
					if(conn != null) {
						String orderDiscountInsertSQL = "INSERT INTO order_discount (ordertable_OrderID, discount_DiscountID) VALUES (?, ?)";
						PreparedStatement orderDiscountInsertStmt = conn.prepareStatement(orderDiscountInsertSQL);
						orderDiscountInsertStmt.setInt(1, orderID);
						orderDiscountInsertStmt.setInt(2, d.getDiscountID());
						orderDiscountInsertStmt.executeUpdate();
					}
				} finally {
					if(conn != null) {
						conn.close();
					}
				}

			}

			// Commit the transaction
			conn.commit();
		} catch(SQLException e) {
			conn.rollback(); // Rollback transaction on error
			throw e;
		} finally {
			conn.setAutoCommit(true); // Reset auto-commit
			conn.close(); // Close the connection
		}
	}


	public static int addPizza(java.util.Date d, int orderID, Pizza p) throws SQLException, IOException
	{
		/*
		 * Add the code needed to insert the pizza into the database.
		 * Keep in mind you must also add the pizza discounts and toppings
		 * associated with the pizza.
		 *
		 * NOTE: there is a Date object passed into this method so that the Order
		 * and ALL its Pizzas can be assigned the same DTS.
		 *
		 * This method returns the id of the pizza just added.
		 *
		 */
		connect_to_db(); // Connect to the database

		// Convert the java.util.Date to java.sql.Timestamp
		java.sql.Timestamp pizzaDate = new java.sql.Timestamp(d.getTime());

		try {
			// Prepare SQL statement for inserting into pizza table
			String pizzaInsertSQL = "INSERT INTO pizza (pizza_Size, pizza_CrustType, pizza_PizzaState, pizza_PizzaDate, pizza_CustPrice, pizza_BusPrice, ordertable_OrderID) VALUES (?, ?, ?, ?, ?, ?, ?)";
			PreparedStatement pizzaInsertStmt = conn.prepareStatement(pizzaInsertSQL, Statement.RETURN_GENERATED_KEYS);

			pizzaInsertStmt.setString(1, p.getSize());
			pizzaInsertStmt.setString(2, p.getCrustType());
			pizzaInsertStmt.setString(3, p.getPizzaState());
			pizzaInsertStmt.setTimestamp(4, pizzaDate);
			pizzaInsertStmt.setDouble(5, p.getCustPrice());
			pizzaInsertStmt.setDouble(6, p.getBusPrice());
			pizzaInsertStmt.setInt(7, orderID);

			// Execute the insert statement
			int affectedRows = pizzaInsertStmt.executeUpdate();

			if (affectedRows == 0) {
				throw new SQLException("Creating pizza failed, no rows affected.");
			}

			// Retrieve the generated PizzaID
			ResultSet generatedKeys = pizzaInsertStmt.getGeneratedKeys();
			int pizzaID;
			if (generatedKeys.next()) {
				pizzaID = generatedKeys.getInt(1);
				p.setPizzaID(pizzaID); // Update the Pizza object with the generated ID
			} else {
				throw new SQLException("Creating pizza failed, no ID obtained.");
			}

			// Handle toppings for the pizza
			for (Topping t : p.getToppings()) {
				// Insert into pizza_topping table
				String pizzaToppingInsertSQL = "INSERT INTO pizza_topping (pizza_PizzaID, topping_TopID, pizza_topping_IsDouble) VALUES (?, ?, ?)";
				PreparedStatement pizzaToppingInsertStmt = conn.prepareStatement(pizzaToppingInsertSQL);
				pizzaToppingInsertStmt.setInt(1, pizzaID);
				pizzaToppingInsertStmt.setInt(2, t.getTopID());
				pizzaToppingInsertStmt.setBoolean(3, t.getDoubled());
				pizzaToppingInsertStmt.executeUpdate();

				// Update topping inventory
				String toppingSelectSQL = "SELECT topping_CurINVT, topping_SmallAMT, topping_MedAMT, topping_LgAMT, topping_XLAMT FROM topping WHERE topping_TopID = ?";
				PreparedStatement toppingSelectStmt = conn.prepareStatement(toppingSelectSQL);
				toppingSelectStmt.setInt(1, t.getTopID());
				ResultSet toppingRS = toppingSelectStmt.executeQuery();

				if (toppingRS.next()) {
					double curINVT = toppingRS.getDouble("topping_CurINVT");
					double amtUsed = 0;
					if (p.getSize().equals(size_s)) {
						amtUsed = toppingRS.getDouble("topping_SmallAMT");
					} else if (p.getSize().equals(size_m)) {
						amtUsed = toppingRS.getDouble("topping_MedAMT");
					} else if (p.getSize().equals(size_l)) {
						amtUsed = toppingRS.getDouble("topping_LgAMT");
					} else if (p.getSize().equals(size_xl)) {
						amtUsed = toppingRS.getDouble("topping_XLAMT");
					}

					if (t.getDoubled()) {
						amtUsed *= 2;
					}

					double newINVT = curINVT - amtUsed;
					if (newINVT < 0) {
						throw new SQLException("Not enough inventory for topping: " + t.getTopName());
					}

					// Update the topping's current inventory
					String toppingUpdateSQL = "UPDATE topping SET topping_CurINVT = ? WHERE topping_TopID = ?";
					PreparedStatement toppingUpdateStmt = conn.prepareStatement(toppingUpdateSQL);
					toppingUpdateStmt.setDouble(1, newINVT);
					toppingUpdateStmt.setInt(2, t.getTopID());
					toppingUpdateStmt.executeUpdate();
				} else {
					throw new SQLException("Topping not found: " + t.getTopName());
				}
			}

			// Handle pizza discounts
			for (Discount dsc : p.getDiscounts()) {
				// Insert into pizza_discount table
				String pizzaDiscountInsertSQL = "INSERT INTO pizza_discount (pizza_PizzaID, discount_DiscountID) VALUES (?, ?)";
				PreparedStatement pizzaDiscountInsertStmt = conn.prepareStatement(pizzaDiscountInsertSQL);
				pizzaDiscountInsertStmt.setInt(1, pizzaID);
				pizzaDiscountInsertStmt.setInt(2, dsc.getDiscountID());
				pizzaDiscountInsertStmt.executeUpdate();
			}

			// Close the connection
			conn.close();

			// Return the PizzaID
			return pizzaID;

		} catch (SQLException e) {
			conn.close(); // Ensure the connection is closed in case of exception
			throw e; // Rethrow the exception
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
		ArrayList<Order> orders = new ArrayList<>();

		// Use try-with-resources to manage Connection
		try (Connection conn = connect_to_db()) {
			// Build the SQL query with parameters
			StringBuilder queryBuilder = new StringBuilder("SELECT * FROM ordertable");
			List<Object> parameters = new ArrayList<>();

			// Append WHERE clause based on status
			if (status == 1) {
				queryBuilder.append(" WHERE ordertable_isComplete = ?");
				parameters.add(false);
			} else if (status == 2) {
				queryBuilder.append(" WHERE ordertable_isComplete = ?");
				parameters.add(true);
			} else if (status == 3) {
				// No additional condition needed
			} else {
				throw new IllegalArgumentException("Invalid status value: " + status);
			}

			// Order the results appropriately (e.g., by OrderID)
			queryBuilder.append(" ORDER BY ordertable_OrderID");
			String query = queryBuilder.toString();

			// Use PreparedStatement to prevent SQL injection and handle parameters
			try (PreparedStatement stmt = conn.prepareStatement(query)) {
				// Set parameters if any
				for (int i = 0; i < parameters.size(); i++) {
					stmt.setObject(i + 1, parameters.get(i));
				}

				// Execute the query and process the ResultSet
				try (ResultSet rs = stmt.executeQuery()) {
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
							String dineInQuery = "SELECT dinein_TableNum FROM dinein WHERE ordertable_OrderID = ?";
							try (PreparedStatement dineInStmt = conn.prepareStatement(dineInQuery)) {
								dineInStmt.setInt(1, orderID);
								try (ResultSet dineInRS = dineInStmt.executeQuery()) {
									int tableNum = 0;
									if (dineInRS.next()) {
										tableNum = dineInRS.getInt("dinein_TableNum");
									}
									order = new DineinOrder(orderID, custID, orderDate, custPrice, busPrice, isComplete, tableNum);
								}
							}
						} else if (orderType.equals(delivery)) {
							// Delivery order
							String deliveryQuery = "SELECT * FROM delivery WHERE ordertable_OrderID = ?";
							try (PreparedStatement deliveryStmt = conn.prepareStatement(deliveryQuery)) {
								deliveryStmt.setInt(1, orderID);
								try (ResultSet deliveryRS = deliveryStmt.executeQuery()) {
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

									String address = String.join("\t", houseNum, street, city, state, zip);
									order = new DeliveryOrder(orderID, custID, orderDate, custPrice, busPrice, isComplete, address, isDelivered);
								}
							}
						} else if (orderType.equals(pickup)) {
							// Pickup order
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
						} else {
							// Unknown order type
							continue; // Skip to the next order
						}

						// Populate the order with pizzas
						ArrayList<Pizza> pizzas = getPizzas(conn, order);
						order.setPizzaList(pizzas);

						// Populate the order with discounts
						ArrayList<Discount> orderDiscounts = getDiscounts(conn, order);
						order.setDiscountList(orderDiscounts);

						// Add the order to the list
						orders.add(order);
					}
				}
			} catch (SQLException | IOException e) {
				// Handle exceptions as needed, possibly logging them
				throw e; // Rethrow the exception after handling
			}

			return orders;
		}
	}

	public static Order getLastOrder() throws SQLException, IOException {
		Order lastOrder = null;
		// Use try-with-resources to manage Connection
		try (Connection conn = connect_to_db()) {
			// Prepare the SQL query to get the last order
			String query = "SELECT * FROM ordertable ORDER BY ordertable_OrderID DESC LIMIT 1";

			// Use PreparedStatement instead of Statement for better security and performance
			try (PreparedStatement stmt = conn.prepareStatement(query);
				 ResultSet rs = stmt.executeQuery()) {

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
						String dineInQuery = "SELECT dinein_TableNum FROM dinein WHERE ordertable_OrderID = ?";
						try (PreparedStatement dineInStmt = conn.prepareStatement(dineInQuery)) {
							dineInStmt.setInt(1, orderID);
							try (ResultSet dineInRS = dineInStmt.executeQuery()) {
								int tableNum = 0;
								if (dineInRS.next()) {
									tableNum = dineInRS.getInt("dinein_TableNum");
								}
								lastOrder = new DineinOrder(orderID, custID, orderDate, custPrice, busPrice, isComplete, tableNum);
							}
						}
					} else if (orderType.equals(delivery)) {
						// Delivery order
						String deliveryQuery = "SELECT * FROM delivery WHERE ordertable_OrderID = ?";
						try (PreparedStatement deliveryStmt = conn.prepareStatement(deliveryQuery)) {
							deliveryStmt.setInt(1, orderID);
							try (ResultSet deliveryRS = deliveryStmt.executeQuery()) {
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

								String address = String.join("\t", houseNum, street, city, state, zip);
								lastOrder = new DeliveryOrder(orderID, custID, orderDate, custPrice, busPrice, isComplete, address, isDelivered);
							}
						}
					} else if (orderType.equals(pickup)) {
						// Pickup order
						String pickupQuery = "SELECT pickup_isPickedUp FROM pickup WHERE ordertable_OrderID = ?";
						try (PreparedStatement pickupStmt = conn.prepareStatement(pickupQuery)) {
							pickupStmt.setInt(1, orderID);
							try (ResultSet pickupRS = pickupStmt.executeQuery()) {
								boolean isPickedUp = false;
								if (pickupRS.next()) {
									isPickedUp = pickupRS.getBoolean("pickup_isPickedUp");
								}
								lastOrder = new PickupOrder(orderID, custID, orderDate, custPrice, busPrice, isPickedUp, isComplete);
							}
						}
					} else {
						// Unknown order type
						throw new SQLException("Unknown order type for OrderID: " + orderID);
					}

					// Populate the order with pizzas
					ArrayList<Pizza> pizzas = getPizzas(conn, lastOrder);
					lastOrder.setPizzaList(pizzas);

					// Populate the order with discounts
					ArrayList<Discount> orderDiscounts = getDiscounts(conn, lastOrder);
					lastOrder.setDiscountList(orderDiscounts);
				}
			}
		} catch (SQLException | IOException e) {
			// Handle exceptions as needed, possibly logging them
			throw e; // Rethrow the exception after handling
		}

		// Return the last order
		return lastOrder;
	}


	public static ArrayList<Order> getOrdersByDate(String date) throws SQLException, IOException {
		ArrayList<Order> orders = new ArrayList<>();

		// Use try-with-resources to manage Connection
		try (Connection conn = connect_to_db()) {
			// Prepare the SQL query using PreparedStatement to prevent SQL injection
			String query = "SELECT * FROM ordertable WHERE DATE(ordertable_OrderDateTime) = ? ORDER BY ordertable_OrderID";
			try (PreparedStatement stmt = conn.prepareStatement(query)) {
				stmt.setString(1, date); // Assuming 'date' is in 'YYYY-MM-DD' format

				// Execute the query and process the ResultSet
				try (ResultSet rs = stmt.executeQuery()) {
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
							String dineInQuery = "SELECT dinein_TableNum FROM dinein WHERE ordertable_OrderID = ?";
							try (PreparedStatement dineInStmt = conn.prepareStatement(dineInQuery)) {
								dineInStmt.setInt(1, orderID);
								try (ResultSet dineInRS = dineInStmt.executeQuery()) {
									int tableNum = 0;
									if (dineInRS.next()) {
										tableNum = dineInRS.getInt("dinein_TableNum");
									}
									order = new DineinOrder(orderID, custID, orderDate, custPrice, busPrice, isComplete, tableNum);
								}
							}
						} else if (orderType.equals(delivery)) {
							// Delivery order
							String deliveryQuery = "SELECT * FROM delivery WHERE ordertable_OrderID = ?";
							try (PreparedStatement deliveryStmt = conn.prepareStatement(deliveryQuery)) {
								deliveryStmt.setInt(1, orderID);
								try (ResultSet deliveryRS = deliveryStmt.executeQuery()) {
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

									String address = String.join("\t", houseNum, street, city, state, zip);
									order = new DeliveryOrder(orderID, custID, orderDate, custPrice, busPrice, isComplete, address, isDelivered);
								}
							}
						} else if (orderType.equals(pickup)) {
							// Pickup order
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
						} else {
							// Unknown order type
							throw new SQLException("Unknown order type for OrderID: " + orderID);
						}

						// Populate the order with pizzas
						ArrayList<Pizza> pizzas = getPizzas(conn, order);
						order.setPizzaList(pizzas);

						// Populate the order with discounts
						ArrayList<Discount> orderDiscounts = getDiscounts(conn, order);
						order.setDiscountList(orderDiscounts);

						// Add the order to the list
						orders.add(order);
					}
				}
			}
		} catch (SQLException | IOException e) {
			// Handle exceptions as needed, possibly logging them
			throw e; // Rethrow the exception after handling
		}

		return orders;
	}

	public static ArrayList<Discount> getDiscountList() throws SQLException, IOException {
		ArrayList<Discount> discounts = new ArrayList<>();

		// Use try-with-resources to manage Connection
		try (Connection conn = connect_to_db()) {
			// Prepare the SQL query using PreparedStatement to prevent SQL injection
			String query = "SELECT discount_DiscountID, discount_DiscountName, discount_Amount, discount_IsPercent FROM discount ORDER BY discount_DiscountName";
			try (PreparedStatement stmt = conn.prepareStatement(query);
				 ResultSet rs = stmt.executeQuery()) {

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
			}
		} catch (SQLException | IOException e) {
			// Handle exceptions as needed, possibly logging them
			throw e; // Rethrow the exception after handling
		}

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
	public static void printProfitByPizzaReport() throws SQLException, IOException
	{
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
		 *
		 */

		connect_to_db(); // Connect to the database

		String query = "SELECT * FROM ProfitByPizza ORDER BY Profit DESC";

		Statement stmt = conn.createStatement();
		ResultSet rs = stmt.executeQuery(query);

		// Print the header
		System.out.printf("%-10s %-15s %-15s %-15s%n", "Size", "Crust Type", "Total Pizzas", "Total Profit");
		System.out.println("---------------------------------------------------------------");

		// Iterate over the results
		while (rs.next()) {
			String size = rs.getString("pizza_Size");
			String crustType = rs.getString("pizza_CrustType");
			int totalPizzas = rs.getInt("TotalPizzas");
			double Profit = rs.getDouble("Profit");

			System.out.printf("%-10s %-15s %-15d $%-14.2f%n", size, crustType, totalPizzas, Profit);
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