
<a name="public.blotter"></a>
### _public_.**blotter** `Table`
| Name | Data type  | PK | FK | UQ  | Not null | Default value | Description |
| --- | --- | :---: | :---: | :---: | :---: | --- | --- |
| id | serial | &#10003; |  |  | &#10003; |  |  |
| name | varchar(60) |  |  |  | &#10003; |  |  |
| version | integer |  |  |  | &#10003; | 1 |  |

#### Constraints
| Name | Type | Column(s) | References | On Update | On Delete | Expression | Description |
|  --- | --- | --- | --- | --- | --- | --- | --- |
| blotter_pk | PRIMARY KEY | id |  |  |  |  |  |

---

<a name="public.order"></a>
### _public_.**order** `Table`
| Name | Data type  | PK | FK | UQ  | Not null | Default value | Description |
| --- | --- | :---: | :---: | :---: | :---: | --- | --- |
| id | serial | &#10003; |  |  | &#10003; |  |  |
| blotter_id | integer |  | &#10003; |  |  |  |  |
| status_id | integer |  | &#10003; |  | &#10003; |  |  |
| portfolio_id | char(24) |  |  |  | &#10003; |  |  |
| order_type_id | integer |  | &#10003; |  | &#10003; |  |  |
| security_id | char(24) |  |  |  | &#10003; |  |  |
| quantity | decimal(18,8) |  |  |  | &#10003; |  |  |
| limit_price | decimal(18,8) |  |  |  |  |  |  |
| order_timestamp | timestamptz |  |  |  | &#10003; | CURRENT_TIMESTAMP |  |
| trade_order_id | integer |  |  |  |  |  |  |
| version | integer |  |  |  | &#10003; | 1 |  |

#### Constraints
| Name | Type | Column(s) | References | On Update | On Delete | Expression | Description |
|  --- | --- | --- | --- | --- | --- | --- | --- |
| order_pk | PRIMARY KEY | id |  |  |  |  |  |
| blotter_order_fk | FOREIGN KEY | blotter_id | [public.blotter](#public.blotter) | CASCADE | SET NULL |  |  |
| order_type_order_fk | FOREIGN KEY | order_type_id | [public.order_type](#public.order_type) | CASCADE | RESTRICT |  |  |
| status_order_fk | FOREIGN KEY | status_id | [public.status](#public.status) | CASCADE | RESTRICT |  |  |

#### Indexes
| Name | Type | Column(s) | Expression(s) | Predicate | Description |
|  --- | --- | --- | --- | --- | --- |
| order_trade_order_ndx | btree | trade_order_id |  |  |  |

---

<a name="public.order_type"></a>
### _public_.**order_type** `Table`
| Name | Data type  | PK | FK | UQ  | Not null | Default value | Description |
| --- | --- | :---: | :---: | :---: | :---: | --- | --- |
| id | serial | &#10003; |  |  | &#10003; |  |  |
| abbreviation | varchar(10) |  |  |  | &#10003; |  |  |
| description | varchar(60) |  |  |  | &#10003; |  |  |
| version | integer |  |  |  | &#10003; | 1 |  |

#### Constraints
| Name | Type | Column(s) | References | On Update | On Delete | Expression | Description |
|  --- | --- | --- | --- | --- | --- | --- | --- |
| order_type_pk | PRIMARY KEY | id |  |  |  |  |  |

---

<a name="public.status"></a>
### _public_.**status** `Table`
| Name | Data type  | PK | FK | UQ  | Not null | Default value | Description |
| --- | --- | :---: | :---: | :---: | :---: | --- | --- |
| id | serial | &#10003; |  |  | &#10003; |  |  |
| abbreviation | varchar(20) |  |  |  | &#10003; |  |  |
| description | varchar(60) |  |  |  | &#10003; |  |  |
| version | integer |  |  |  | &#10003; | 1 |  |

#### Constraints
| Name | Type | Column(s) | References | On Update | On Delete | Expression | Description |
|  --- | --- | --- | --- | --- | --- | --- | --- |
| order_status_pk | PRIMARY KEY | id |  |  |  |  |  |

---

Generated at _2025-05-23T07:23:23_ by **pgModeler 1.2.0-beta1**
[PostgreSQL Database Modeler - pgmodeler.io ](https://pgmodeler.io)
Copyright © 2006 - 2025 Raphael Araújo e Silva 
