USE PizzaDB;
DELIMITER //
-- sp 1
CREATE PROCEDURE ADDCUSTOMER(IN FNAME VARCHAR(30), IN LNAME VARCHAR(30), IN PHONENUM VARCHAR(30))
BEGIN
    INSERT INTO customer (customer_FName, customer_LName, customer_PhoneNum)
    VALUES (FNAME, LNAME, PHONENUM);
end //

-- Sp 2
CREATE PROCEDURE CREATEORDER(IN CUSTID INT, IN ORDERTYPE VARCHAR(30), IN ORDERDATETIME DATETIME, IN CUSTPRICE DECIMAL(5,2), IN BUSPRICE DECIMAL(5,2), IN ORDERCOMPLETE TINYINT(1))
BEGIN
    INSERT INTO ordertable (customer_CustID, ordertable_OrderType, ordertable_OrderDateTime, ordertable_CustPrice, ordertable_BusPrice, ordertable_isComplete)
    VALUES (CUSTID, ORDERTYPE, ORDERDATETIME, CUSTPRICE, BUSPRICE, ORDERCOMPLETE);
end //
-- funct 1
CREATE FUNCTION CUSTTOTAL(ORDERID INT) RETURNS DECIMAL(5,2)
DETERMINISTIC
BEGIN
    DECLARE TOTAL DECIMAL(5,2);
    SELECT SUM(pizza_CustPrice) INTO TOTAL
    FROM pizza
    WHERE ordertable_OrderID = ORDERID;
    RETURN TOTAL;
end //
-- funct 2
CREATE FUNCTION BUSTOTAL(ORDERID INT) RETURNS DECIMAL(5,2)
DETERMINISTIC
BEGIN
    DECLARE TOTAL DECIMAL(5,2);
    SELECT SUM(pizza_bUSPrice) INTO TOTAL
    FROM pizza
    WHERE ordertable_OrderID = ORDERID;
    RETURN TOTAL;
end //

-- up trigger 1
CREATE TRIGGER PIZZASTATUS AFTER UPDATE ON pizza
FOR EACH ROW
BEGIN
    IF NEW.pizza_PizzaState = 'completed' THEN
        UPDATE ordertable
        SET ordertable_isComplete = 1
        WHERE ordertable_OrderID = NEW.ordertable_OrderID;
    end if;
end //

-- up trigger 2
CREATE TRIGGER BEFOREPIZZASTATUS
AFTER UPDATE ON pizza
FOR EACH ROW
BEGIN
    IF NEW.pizza_PizzaState = 'in progress' THEN
        UPDATE ordertable
        SET ordertable_isComplete = 0
        WHERE ordertable_OrderID = NEW.ordertable_OrderID;
    end if;
end //

-- insert trigger 1
CREATE TRIGGER DEFORDERTYPE
BEFORE INSERT ON ordertable
FOR EACH ROW
    BEGIN
        IF NEW.ordertable_OrderType IS NULL THEN
            SET NEW.ordertable_OrderType = 'dinein';
        end if;
    end //

-- insert trigger 2
CREATE TRIGGER DEFTOPPINGINV
BEFORE INSERT ON topping
FOR EACH ROW
    BEGIN
        IF NEW.topping_CurINVT IS NULL THEN
            SET NEW.topping_CurINVT = 100;
        end if;
    end //

DELIMITER ;

