
DROP SCHEMA IF EXISTS PizzaDB;
CREATE SCHEMA PizzaDB;
USE PizzaDB;

CREATE TABLE customer (
    customer_CustID INT PRIMARY KEY AUTO_INCREMENT ,
    customer_FName VARCHAR(30) NOT NULL ,
    customer_LName VARCHAR(30) NOT NULL ,
    customer_PhoneNum VARCHAR(30) NOT NULL
);

CREATE TABLE ordertable (
    ordertable_OrderID INT PRIMARY KEY AUTO_INCREMENT,
    customer_CustID INT ,
    ordertable_OrderType VARCHAR(30) NOT NULL ,
    ordertable_OrderDateTime DATETIME NOT NULL ,
    ordertable_CustPrice DECIMAL(5,2) NOT NULL ,
    ordertable_BusPrice DECIMAL(5,2) NOT NULL ,
    ordertable_isComplete TINYINT(1) DEFAULT 0,
    FOREIGN KEY (customer_CustID) REFERENCES customer(customer_CustID)
);

CREATE TABLE pickup (
    ordertable_OrderID INT PRIMARY KEY ,
    pickup_IsPickedUp TINYINT NOT NULL DEFAULT 0,
    CONSTRAINT pickup_fk FOREIGN KEY (ordertable_OrderID) REFERENCES ordertable(ordertable_OrderID) ON DELETE CASCADE
);

CREATE TABLE delivery (
    ordertable_OrderID INT PRIMARY KEY ,
    delivery_HouseNum INT NOT NULL ,
    delivery_Street VARCHAR(30) NOT NULL ,
    delivery_City VARCHAR(30) NOT NULL ,
    delivery_State VARCHAR(2) NOT NULL ,
    delivery_Zip INT NOT NULL ,
    delivery_IsDelivered TINYINT NOT NULL DEFAULT 0 ,
    CONSTRAINT delivery_fk FOREIGN KEY (ordertable_OrderID) REFERENCES ordertable(ordertable_OrderID) ON DELETE CASCADE
);

CREATE TABLE dinein (
    ordertable_OrderID INT PRIMARY KEY ,
    dinein_TableNum INT NOT NULL ,
    CONSTRAINT dinein_fk FOREIGN KEY (ordertable_OrderID) REFERENCES ordertable(ordertable_OrderID) ON DELETE CASCADE
);

CREATE TABLE baseprice (
    baseprice_Size VARCHAR(30) NOT NULL ,
    baseprice_CrustType VARCHAR(30) NOT NULL ,
    baseprice_CustPrice DECIMAL(5,2) NOT NULL ,
    baseprice_BusPrice DECIMAL(5,2) NOT NULL ,
    CONSTRAINT baseprice_pk PRIMARY KEY (baseprice_CrustType, baseprice_Size)
);

CREATE TABLE pizza (
    pizza_PizzaID INT PRIMARY KEY AUTO_INCREMENT ,
    pizza_Size VARCHAR(30) NOT NULL ,
    pizza_CrustType VARCHAR(30) NOT NULL ,
    pizza_PizzaState VARCHAR(30) NOT NULL ,
    pizza_PizzaDate DATETIME NOT NULL ,
    pizza_CustPrice DECIMAL(5,2) NOT NULL ,
    pizza_BusPrice DECIMAL(5,2) NOT NULL ,
    ordertable_OrderID INT NOT NULL ,
    CONSTRAINT pizza_baseprice_fk FOREIGN KEY (pizza_CrustType, pizza_Size) REFERENCES baseprice(baseprice_CrustType, baseprice_Size) ,
    CONSTRAINT pizza_ordertable_fk FOREIGN KEY (ordertable_OrderID) REFERENCES ordertable(ordertable_OrderID) ON DELETE CASCADE
);

CREATE TABLE topping (
    topping_TopID INT PRIMARY KEY AUTO_INCREMENT ,
    topping_TopName VARCHAR(30) NOT NULL ,
    topping_SmallAMT DECIMAL(5,2) NOT NULL ,
    topping_MedAMT DECIMAL(5,2) NOT NULL ,
    topping_LgAMT DECIMAL(5,2) NOT NULL ,
    topping_XLAMT DECIMAL(5,2) NOT NULL ,
    topping_CustPrice DECIMAL(5,2) NOT NULL ,
    topping_BusPrice DECIMAL(5,2) NOT NULL ,
    topping_MinINVT INT NOT NULL ,
    topping_CurINVT INT NOT NULL
);

CREATE TABLE pizza_topping (
    pizza_PizzaID INT ,
    topping_TopID INT ,
    pizza_topping_IsDouble INT NOT NULL ,
    CONSTRAINT pizza_topping_pk PRIMARY KEY (pizza_PizzaID, topping_TopID) ,
    CONSTRAINT pizza_top_pizza_fk FOREIGN KEY (pizza_PizzaID) REFERENCES pizza(pizza_PizzaID) ,
    CONSTRAINT pizza_top_topping_fk FOREIGN KEY (topping_TopID) REFERENCES topping(topping_TopID)
);

CREATE TABLE discount (
    discount_DiscountID INT PRIMARY KEY AUTO_INCREMENT ,
    discount_DiscountName VARCHAR(30) NOT NULL ,
    discount_Amount DECIMAL(5,2) NOT NULL ,
    discount_IsPercent TINYINT NOT NULL
);

CREATE TABLE pizza_discount (
    pizza_PizzaID INT ,
    discount_DiscountID INT ,
    CONSTRAINT pizza_discount_pk PRIMARY KEY (pizza_PizzaID, discount_DiscountID) ,
    CONSTRAINT pizza_disc_pizza_fk FOREIGN KEY (pizza_PizzaID) REFERENCES pizza(pizza_PizzaID) ,
    CONSTRAINT pizza_disc_discount_fk FOREIGN KEY (discount_DiscountID) REFERENCES discount(discount_DiscountID)
);

CREATE TABLE order_discount (
    ordertable_OrderID INT ,
    discount_DiscountID INT ,
    CONSTRAINT order_discount_pk PRIMARY KEY (ordertable_OrderID, discount_DiscountID) ,
    CONSTRAINT order_disc_order_fk FOREIGN KEY (ordertable_OrderID) REFERENCES ordertable(ordertable_OrderID) ,
    CONSTRAINT order_disc_discount_fk FOREIGN KEY (discount_DiscountID) REFERENCES discount(discount_DiscountID)
);