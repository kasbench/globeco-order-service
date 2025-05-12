-- GlobeCo Order Service Schema Migration
-- Initial schema migration generated from documentation/order-service.sql

SET search_path TO public;

-- Table: blotter
CREATE TABLE blotter (
    id serial NOT NULL,
    name varchar(60) NOT NULL,
    version integer NOT NULL DEFAULT 1,
    CONSTRAINT blotter_pk PRIMARY KEY (id)
);

-- Table: order
CREATE TABLE "order" (
    id serial NOT NULL,
    blotter_id integer,
    status_id integer NOT NULL,
    portfolio_id char(24) NOT NULL,
    order_type_id integer NOT NULL,
    security_id char(24) NOT NULL,
    quantity decimal(18,8) NOT NULL,
    limit_price decimal(18,8),
    order_timestamp timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version integer NOT NULL DEFAULT 1,
    CONSTRAINT order_pk PRIMARY KEY (id)
);

-- Table: order_type
CREATE TABLE order_type (
    id serial NOT NULL,
    abbreviation varchar(10) NOT NULL,
    description varchar(60) NOT NULL,
    version integer NOT NULL DEFAULT 1,
    CONSTRAINT order_type_pk PRIMARY KEY (id)
);

-- Table: status
CREATE TABLE status (
    id serial NOT NULL,
    abbreviation varchar(20) NOT NULL,
    description varchar(60) NOT NULL,
    version integer NOT NULL DEFAULT 1,
    CONSTRAINT order_status_pk PRIMARY KEY (id)
);

-- Foreign Key Constraints
ALTER TABLE "order" ADD CONSTRAINT blotter_order_fk FOREIGN KEY (blotter_id)
REFERENCES blotter (id) MATCH FULL
ON DELETE SET NULL ON UPDATE CASCADE;

ALTER TABLE "order" ADD CONSTRAINT order_type_order_fk FOREIGN KEY (order_type_id)
REFERENCES order_type (id) MATCH FULL
ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE "order" ADD CONSTRAINT status_order_fk FOREIGN KEY (status_id)
REFERENCES status (id) MATCH FULL
ON DELETE RESTRICT ON UPDATE CASCADE; 