-- ** Database generated with pgModeler (PostgreSQL Database Modeler).
-- ** pgModeler version: 1.2.0-beta1
-- ** PostgreSQL version: 17.0
-- ** Project Site: pgmodeler.io
-- ** Model Author: ---

-- ** Database creation must be performed outside a multi lined SQL file. 
-- ** These commands were put in this file only as a convenience.

-- object: postgres | type: DATABASE --
-- DROP DATABASE IF EXISTS postgres;
--CREATE DATABASE postgres;
-- ddl-end --


SET search_path TO pg_catalog,public;
-- ddl-end --

-- object: public.blotter | type: TABLE --
-- DROP TABLE IF EXISTS public.blotter CASCADE;
CREATE TABLE public.blotter (
	id serial NOT NULL,
	name varchar(60) NOT NULL,
	version integer NOT NULL DEFAULT 1,
	CONSTRAINT blotter_pk PRIMARY KEY (id)
);
-- ddl-end --
ALTER TABLE public.blotter OWNER TO postgres;
-- ddl-end --

-- object: public."order" | type: TABLE --
-- DROP TABLE IF EXISTS public."order" CASCADE;
CREATE TABLE public."order" (
	id serial NOT NULL,
	blotter_id integer,
	status_id integer NOT NULL,
	portfolio_id char(24) NOT NULL,
	order_type_id integer NOT NULL,
	security_id char(24) NOT NULL,
	quantity decimal(18,8) NOT NULL,
	limit_price decimal(18,8),
	order_timestamp timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
	trade_order_id integer,
	version integer NOT NULL DEFAULT 1,
	CONSTRAINT order_pk PRIMARY KEY (id)
);
-- ddl-end --
--ALTER TABLE public."order" OWNER TO postgres;
-- ddl-end --

-- object: blotter_order_fk | type: CONSTRAINT --
-- ALTER TABLE public."order" DROP CONSTRAINT IF EXISTS blotter_order_fk CASCADE;
ALTER TABLE public."order" ADD CONSTRAINT blotter_order_fk FOREIGN KEY (blotter_id)
REFERENCES public.blotter (id) MATCH FULL
ON DELETE SET NULL ON UPDATE CASCADE;
-- ddl-end --

-- object: public.order_type | type: TABLE --
-- DROP TABLE IF EXISTS public.order_type CASCADE;
CREATE TABLE public.order_type (
	id serial NOT NULL,
	abbreviation varchar(10) NOT NULL,
	description varchar(60) NOT NULL,
	version integer NOT NULL DEFAULT 1,
	CONSTRAINT order_type_pk PRIMARY KEY (id)
);
-- ddl-end --
ALTER TABLE public.order_type OWNER TO postgres;
-- ddl-end --

-- object: order_type_order_fk | type: CONSTRAINT --
-- ALTER TABLE public."order" DROP CONSTRAINT IF EXISTS order_type_order_fk CASCADE;
ALTER TABLE public."order" ADD CONSTRAINT order_type_order_fk FOREIGN KEY (order_type_id)
REFERENCES public.order_type (id) MATCH FULL
ON DELETE RESTRICT ON UPDATE CASCADE;
-- ddl-end --

-- object: public.status | type: TABLE --
-- DROP TABLE IF EXISTS public.status CASCADE;
CREATE TABLE public.status (
	id serial NOT NULL,
	abbreviation varchar(20) NOT NULL,
	description varchar(60) NOT NULL,
	version integer NOT NULL DEFAULT 1,
	CONSTRAINT order_status_pk PRIMARY KEY (id)
);
-- ddl-end --
ALTER TABLE public.status OWNER TO postgres;
-- ddl-end --

-- object: status_order_fk | type: CONSTRAINT --
-- ALTER TABLE public."order" DROP CONSTRAINT IF EXISTS status_order_fk CASCADE;
ALTER TABLE public."order" ADD CONSTRAINT status_order_fk FOREIGN KEY (status_id)
REFERENCES public.status (id) MATCH FULL
ON DELETE RESTRICT ON UPDATE CASCADE;
-- ddl-end --

-- object: order_trade_order_ndx | type: INDEX --
-- DROP INDEX IF EXISTS public.order_trade_order_ndx CASCADE;
CREATE UNIQUE INDEX order_trade_order_ndx ON public."order"
USING btree
(
	trade_order_id
);
-- ddl-end --


