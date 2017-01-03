# DbManPlus

SQLite database manager for Android

## Introduction

`DbManPlus` is based on `DatabaseManager` by JakarCo implemented at [Android SQLite Library](http://androidslitelibrary.com).

The aim is to provide a thread-safe implementation of SQLite database manager, which is easy to use and has minimal boilerplate code. The API is very close to `ContentProvider` on Android.

For reactive extension to `DbManPlus`, check out [RxDbManPlus](https://github.com/vssh/rxdbmanplus).

## Usage

### Extend `DbManPlus` class
Extend the `DbManPlus` and override `onCreate` and `onUpdate` functions.

``` java
public class MyDbManager extends DbManPlus {

    public MyDbManager(Context context) {
        super(context, DATABASE_NAME, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + TABLE_NAME + " ("
                + COLUMN_ID + " INTEGER PRIMARY KEY,"
                + COLUMN_TIME + " INTEGER,"
                + COLUMN_NAME + " TEXT"
                + ");");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_NAME);
        onCreate(db);
    }
}
```

### Create a new instance
To use this database, first always create a new `MyDbManager` instance.

``` java
MyDbManager dbManager = new MyDbManager(this);
```

### Write to database
Then this instance can be used to write to database using `insert`, `bulkInsert`, `update` and `delete` functions. Each of these functions opens a new `DbManPlus` connection and closes it on completion. `DbManPlus` holds a database connection as long as at least one `DbManPlus` connection is open.
In effect, all write operations are performed sequentially on a single database connection. There is no need to manage any connections.

#### insertcontentValues
`insert` operation takes the table name and `ContentValues` and returns the `rowId` of inserted row on success and `-1` on failure.
``` java
int result = dbManager.insert(TABLE_NAME, contentValues);
```

#### bulkInsert
`bulkInsert` takes table name and array of `ContentValues` and returns the number of rows inserted on success, `-1` on failure.
``` java
int result = dbManager.bulkInsert(TABLE_NAME, contentValues);
```

#### update
`update` takes table name, `ContentValues`, `selection` string and `selectionArgs` array. This is similar to ContentProvider on Android.
It returns the number of rows updated on success and `-1` on failure.
``` java
int result = dbManager.update(TABLE_NAME, contentValues, selection, selectionArgs);
```

#### delete
`delete` takes table name, `selection` string and `selectionArgs` array and returns the number of rows updated on success and `-1` on failure.
``` java
int result = dbManager.delete(TABLE_NAME, selection, selectionArgs);
```

### Read from database
The same instance can also be used to query the database. Each query will open a new `DbManPlus` connection. This connection is not automatically closed. Each query returns a special `Cursor`. After using the `Cursor`, it should be closed. Closing it will also close the connection.

#### query
`query` has similar interface to `ContentProvider`. It takes table names, `projection`, `selection`, `selectionArgs`, [`groupBy`], [`having`], `sortOrder` and [`limit`] and returns a `Cursor`.
``` java
Cursor cursor = dbManager.query(TABLE_NAMES, projection, selection, selectionArgs, sortOrder);
```
``` java
Cursor cursor = dbManager.query(TABLE_NAMES, projection, selection, selectionArgs, groupBy, having, sortOrder, limit);
```
**NOTE: Remember to close the `Cursor` after use as it holds a database connection open if not closed. When using it in an `Activity` or `Fragment`, tie it to the respective lifecycle.**

#### rawQuery
`rawQuery` allows for raw SQL queries to be run on the database. It takes an sql string and and `selectionArgs` and returns a `Cursor`.
``` java
Cursor cursor = dbManager.rawQuery(String sql, String[] selectionArgs);
```
**NOTE: Remember to close the `Cursor` after use as it holds a database connection open if not closed. When using it in an `Activity` or `Fragment`, tie it to the respective lifecycle.**

### Transactions
`DbManPlus` also supports transactions. The database operations inside a transaction are all committed if successful, or none are committed.

#### doTransaction
`doTransaction` takes a callable function and returns `true` if successful or `false` otherwise. The callable function is called inside a transaction and all the database functions will all be committed or will all fail.
``` java
boolean result = dbManager.doTransaction(new Callable<Boolean>() {
    @Override
    public Boolean call() throws Exception {
        //do something...
        dbManager.insert(...);
        //do something...
        dbManager.update(...);
        //do something...
        dbmanager.delete(...);

        return true;
    }
});
```

### Encrypted exports and imports
`DbManPlus` allows you to export(backup) and import(overwrite) the database with ease.

#### exportDB
`exportDB` copies the current database state to the given path, encrypted using a salt and password.
```java
long size = exportDB("path/to/export/location", "salt", "password");
```

#### importDB
`importDB` overwrites the current database with the decrypted database from a path or stream.
```java
long size = importDB("path/to/import/location", "salt", "password");

long size = importDB(importStream, "salt", "password");
```

### Interface with database using objects
`DbManPlus` can read from or write to the database directly using objects that correspond to a table row. This requires extending `DbModel` and `DbItem` for each table.

Extending `DBItem`, define the variables and then use `getVal` and `setVal` to map these variables to correct table columns.
***Note: Be careful with `setVal` to use the correct type from the table column. Only the correct type parameter is set, all others are `null`. A `null` in the expected parameter suggests the column is not set.
```java
public class TableItem extends DbItem {
    private int id;
    private String name;
    private long time;
    
    //constructor, getters, setters...
    
    @Override
    protected void setVal(String column, Long integerVal, Double floatingPointVal, String textVal, byte[] blobVal) {
        switch (column) {
            case TableModel.Columns._ID:
                if(integerVal != null) id = integerVal.intValue();
                break;
            case TableModel.Columns.NAME:
                if(textVal != null) name = textVal;
                break;
            case TableModel.Columns.TIMESTAMP:
                if(integerVal != null) time = integerVal;
                break;
        }
    }

    @Override
    protected Object getVal(String column) {
        switch (column) {
            case TableModel.Columns.TIMESTAMP:
                return time;
            case TableModel.Columns._ID:
                return id;
            case TableModel.Columns.NAME:
                return name;
        }
        return null;
    }
} 
```

If more control is needed, instead of overriding `getVal` and `setVal`, override `fromCursor` and `toContentValues` directly.
```java
@NonNull
@Override
public ContentValues toContentValues(String[] columns) {
    ContentValues values = new ContentValues();

    values.put(TableModel.Columns._ID, id);
    values.put(TableModel.Columns.NAME, name);
    values.put(TableModel.Columns.TIMESTAMP, time);
    
    return values;
}

@Override
public void fromCursor(Cursor cursor, String[] columns) {
    this.id = cursor.getInt(cursor.getColumnIndex(TableModel.Columns._ID));
    this.date = cursor.getString(cursor.getColumnIndex(TableModel.Columns.DATE));
    this.timezone = cursor.getString(cursor.getColumnIndex(TableModel.Columns.TIMEZONE));
}
```


Next, extend the DbModel and define the table columns.
```java
public class TableModel extends DbModel<TableItem> {

    public static final String TABLE_NAME = "table";

    public TableModel(DbManagerPlus dbManager) {
        super(dbManager);
    }

    public static final String DEFAULT_SORT_ORDER = TableModel.Columns.TIMESTAMP+" ASC";

    @Override
    public String getTableName() {
        return SensorModel.TABLE_NAME;
    }

    @Override
    public boolean continueInsert(ContentValues values) {
        if (!values.containsKey(SensorModel.Columns.TIMESTAMP)) {
            return false;
        }
        return true;
    }

    @NonNull
    @Override
    protected String[] getTableColumns() {
        return new String[] {
                Columns._ID,
                Columns.TIMESTAMP,
                Columns.NAME
        };
    }

    public static final class Columns implements BaseColumns {
        // This class cannot be instantiated
        private Columns() {}

        public static final String TIMESTAMP = "timestamp";

        public static final String NAME = "name";
    }
        
    //add convenience methods here...    
}
```

Now, you are ready to write to and read from the database using the `TableItem` objects.
```java
    TableModel tableModel = new TableModel(dbManPlus);
    
    //insert with ContentValues
    int rowId = tableModel.insert(contentValues);
    
    //insert with TableItem
    int rowId = tableModel.insert(tableItem);
    
    //update with ContentValues
    int numRows = tableModel.update(contentValues, selection, selectionArgs);
    
    //update with TableItem
    int numRows = tableModel.update(tableItem, selection, selectionArgs);
    
    //simple query
    Cursor cursor = tableModel.query(projection, selection, selectionArgs, sortOrder);
    
    //query as list
    List<TableItem> = tableModel.queryAsList(projection, selection, selectionArgs, sortOrder);
```