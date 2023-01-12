# DatabaseAPI
This API is a help to create your database more easily and simply.

First of all, it will be necessary to create the database configuration, this will be separated by two types, MySQL and SQLite.
For the MySQL configuration we will use the following arguments in the DatabaseConfiguration constructor:

**MySLQ**

```java
DatabaseConfiguration(String ip, int port, String user, String password) { // mysql
```
**SQLite (local file)**

```java
DatabaseConfiguration(boolean generateIdentifiers, File dataFolder) { // sql
```

For this example we will use SQLite, which has no bearing on the functionality of the API.

```java
DatabaseConfiguration dbConfig = new DatabaseConfiguration(true, new File("C:\Users\youruser\Desktop"));
```

With this, with each database registered in the API, a *name.db* file will be created (ONLY IN THE CASE OF USING SQLITE)

Next it will be necessary to create the API instance, it is abstract and we must add the necessary methods. In addition, it will also be necessary to add the configuration created previously.

```java
DatabaseAPI databaseAPI = new DatabaseAPI(configuration) {
    @Override
    public void log(String name, String message) {
        System.out.println("[" + name + "]: " + message); // here you print with your log system...
    }
    @Override
    public boolean isPlayerOnline(UUID uuid) {
        return yoursystem.getPlayer(uuid) != null; // this you need return if player is online (for internal actions)
    }
    @Override
    public void kickPlayer(UUID uuid, String message) {
        yoursystem.getPlayer(uuid).kick(message); // this method is used to kick a player in the event that loading/saving returned an error.
    }         
};
```
When generating the API instance, we will automatically create two databases *PlayerDatabase* and *ServerDatabase* you can use any of these databases to your liking, but you can also create your own database.
For this it will be necessary to create a class extending it from Database, for example we will create the TestDatabase database.

```java
import me.pepe.DatabaseAPI.DatabaseManager.Types.Database;

public class TestDatabase extends Database {
    public TestDatabase() {
        super("TestDatabase"); // name of database
    }
}
```
Once the extended Database class is created, it will be necessary to register the database to the API, using the DatabaseManager we will register the database which we must initialize.

```java
DatabaseManager dbManager = databaseAPI.getDatabaseManager();
dbManager.registerDatabase(new TestDatabase());
Database testdb = dbManager.getDatabase(TestDatabase.class);
```
As we have seen in the previous code, the databases in the API are saved using their own class as an identifier.

It is time to integrate tables in our database, for this, as before with the database itself, it will be necessary to create a class per table.
Before continuing, I suppose you have noticed that the database has a built-in system for players, this is geared towards videogames or client systems. In the event that your software does not contain players or clients, you should not worry, this is completely separated in the code and does not imply any extra consumption.

**The explanation of the client/player oriented API is at the end.**

Once here it is important to know the types of tables.

- **DatabaseTable**: It is the parent class, from which we can create the different types of tables. It is a basic table with its key and its attributes, to get a tuple we will use its key.
- **TableDatabaseMultiKeys**: It extends from TableDatabase, it refers to its name, all the attributes can be used as "key", but it was really created thinking that its key was a number and that this auto incremented automatically as tuples are added. When trying to search for data in said table, it can return several tuples depending on the search method we use.

***(For client/player system)**

Table types available for client/player

- **PlayerDatabaseTable**: It is the parent class, it will need the PlayerData of the client/player to be able to load/generate the data. It has an *update* method to update the data in the table, to execute this method it has a cooldown of 10 seconds so as not to saturate the database, although we will have the option of setting update priority so that it is always updated when using the method. We should not worry about the loss of data, because when downloading the PlayerData it will be saved automatically if it is not updated. From this class we can generate new table types for client/player.
- **SimplePlayerDatabaseTable**: As its name indicates it is simply the child of *PlayerDataDatabase*.
- **MultiPlayerDatabaseTable**: