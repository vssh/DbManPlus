# dbmanplus

SQLite database manager for Android

## Introduction

`dbmanplus` is based on `DatabaseManager` by JakarCo implemented at [Android SQLite Library](http://androidslitelibrary.com).

The aim is to provide a thread-safe implementation of SQLite database manager, which is easy to use and has minimal boilerplate code. The API is very close to `ContentProvider` on Android.

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
Then this instance can be used to write to database using `insert`, `bulkInsert`, `update` and `delete` functions. Each of these functions opens a new `dbmanplus` connection and closes it on completion. `dbmanplus` holds a database connection as long as at least one `dbmanplus` connection is open.
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
The same instance can also be used to query the database. Each query will open a new `dbmanplus` connection. This connection is not automatically closed. Each query returns a special `Cursor`. After using the `Cursor`, it should be closed. Closing it will also close the connection.

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
`dbmanplus` also supports transactions. The database operations inside a transaction are all committed if successful, or none are committed.

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
