USE PizzaDB;

-- View ToppingPopularity
CREATE VIEW ToppingPopularity AS
    SELECT topping_TopName AS Topping,
           CAST(
                   SUM(
                           CASE
                               WHEN pizza_topping_IsDouble = 1 THEN 2
                               WHEN pizza_topping_IsDouble = 0 THEN 1
                               ELSE 0
                           END
                   ) AS DECIMAL (33, 0)
           ) AS ToppingCount
    FROM topping LEFT JOIN pizza_topping ON topping.topping_TopID = pizza_topping.topping_TopID
    GROUP BY topping_TopName
    ORDER BY `ToppingCount` DESC, 'Topping' ASC;

-- View ProfitByPizza
CREATE VIEW ProfitByPizza AS
    SELECT pizza_Size AS Size,
           pizza_CrustType AS Crust,
           ROUND(SUM(pizza_CustPrice - pizza_BusPrice),2) AS Profit,
           DATE_FORMAT(pizza_PizzaDate, '%c/%Y') AS OrderMonth
    FROM pizza
    GROUP BY pizza_Size, pizza_CrustType, `OrderMonth`
    ORDER BY `Profit`;


-- View ProfitByOrderType
CREATE VIEW ProfitByOrderType AS
    SELECT ordertable_OrderType AS customerType,
           CONCAT(MONTH(ordertable_OrderDateTime), '/', YEAR(ordertable_OrderDateTime)) AS OrderMonth,
           ROUND(SUM(ordertable_CustPrice),2) AS TotalOrderPrice,
           ROUND(SUM(ordertable_BusPrice),2) AS TotalOrderCost,
           ROUND(SUM(ordertable_CustPrice - ordertable_BusPrice),2) AS Profit
    FROM ordertable
    GROUP BY `customerType`, `OrderMonth`
    UNION
    SELECT
        NULL,
        'Grand Total',
        SUM(ordertable_CustPrice),
        SUM(ordertable_BusPrice),
        SUM(ordertable_CustPrice - ordertable_BusPrice)
    FROM ordertable;