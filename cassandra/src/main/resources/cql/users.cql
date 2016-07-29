create keyspace test with replication = {'class':'SimpleStrategy', 'replication_factor':3};
use test;
create table users ( id int primary key, name text );
insert into users (id,name) values (1, 'oscerd');
quit;
