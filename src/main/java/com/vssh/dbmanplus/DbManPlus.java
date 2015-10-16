package com.vssh.dbmanplus;

/**
 * Created by varun on 24.08.15.
 *
 *  Extend this class and use it as an SQLiteOpenHelper class
 *
 *  Based on DatabaseManager by JakarCo implemented at <a href="http://androidslitelibrary.com">Android SQLite Library</a>
 */

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;

import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;

import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteQueryBuilder;
import android.support.annotation.CallSuper;

import java.util.concurrent.atomic.AtomicInteger;

abstract public class DbManPlus {

    /**
     * See SQLiteOpenHelper documentation
     */
    abstract public void onCreate(SQLiteDatabase db);

    /**
     * See SQLiteOpenHelper documentation
     */
    abstract public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion);

    /**
     * Optional.
     * *
     */
    public void onOpen(SQLiteDatabase db) {
    }

    /**
     * Optional.
     */
    public void onDowngrade(SQLiteDatabase db, int oldVersion, int newVersion) {
    }

    /**
     * Optional
     */
    public void onConfigure(SQLiteDatabase db) {
    }


    /**
     * The SQLiteOpenHelper class is not actually used by your application.
     */
    static private class DBSQLiteOpenHelper extends SQLiteOpenHelper {

        DbManPlus dbManPlus;
        private AtomicInteger counter = new AtomicInteger(0);

        public DBSQLiteOpenHelper(Context context, String name, int version, DbManPlus dbManPlus) {
            super(context, name, null, version);
            this.dbManPlus = dbManPlus;
        }

        public int addConnection() {
            return counter.incrementAndGet();
        }

        public int removeConnection() {
            if (counter.get() > 0) return counter.decrementAndGet();
            return counter.get();
        }

        /*public int getCounter() {
            return counter.get();
        }*/

        @Override
        public void onCreate(SQLiteDatabase db) {
            dbManPlus.onCreate(db);
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            dbManPlus.onUpgrade(db, oldVersion, newVersion);
        }

        @Override
        public void onOpen(SQLiteDatabase db) {
            dbManPlus.onOpen(db);
        }

        @Override
        public void onDowngrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            dbManPlus.onDowngrade(db, oldVersion, newVersion);
        }

        @Override
        public void onConfigure(SQLiteDatabase db) {
            dbManPlus.onConfigure(db);
        }
    }

    private static final ConcurrentHashMap<String, DBSQLiteOpenHelper> dbMap = new ConcurrentHashMap<>();

    //private Object dbLockObject;

    private static final Object managerLockObject = new Object();


    private DBSQLiteOpenHelper sqLiteOpenHelper;
    private SQLiteDatabase db;
    //private Context context;

    /**
     * Instantiate a new DB Helper.
     * <br> SQLiteOpenHelpers are statically cached so they (and their internally cached SQLiteDatabases) will be reused for concurrency
     *
     * @param context Any {@link android.content.Context} belonging to your package.
     * @param name    The database name. This may be anything you like. Adding a file extension is not required and any file extension you would like to use is fine.
     * @param version the database version.
     */
    public DbManPlus(Context context, String name, int version) {
        String dbPath = context.getApplicationContext().getDatabasePath(name).getAbsolutePath();
        synchronized (managerLockObject) {
            sqLiteOpenHelper = dbMap.get(dbPath);
            if (sqLiteOpenHelper == null) {
                sqLiteOpenHelper = new DBSQLiteOpenHelper(context, name, version, this);
                dbMap.put(dbPath, sqLiteOpenHelper);
                //dbLockObject = new Object();
                //mSubjectList = new ArrayList<Subject>();
            }
            //SQLiteOpenHelper class caches the SQLiteDatabase, so this will be the same SQLiteDatabase object every time
            db = sqLiteOpenHelper.getWritableDatabase();
        }
        //this.context = context.getApplicationContext();
    }

    /**
     * Check if the underlying SQLiteDatabase is open
     *
     * @return whether the DB is open or not (checks for null, to prevent crashing)
     */
    public boolean isOpen() {
        return (db != null && db.isOpen());
    }

    /**
     * Check if the SQliteDatabase is in transaction
     * @return true if in transaction, else false
     */
    public boolean inTransaction() {
        return (db != null && db.isOpen() && db.inTransaction());
    }


    /**
     * Lowers the DB counter by 1 for any {@link DbManPlus}s referencing the same DB on disk
     * <br />If the new counter is 0, then the database will be closed.
     * <br /><br />This needs to be called before application exit.
     *
     * @return true if the underlying {@link android.database.sqlite.SQLiteDatabase} is closed (counter is 0), and false otherwise (counter > 0)
     */
    protected boolean close() {
        synchronized (managerLockObject) {
            int count = sqLiteOpenHelper.removeConnection();
            //Log.w("LockCount--", "" + count);
            if (count == 0) {
                try {
                    if (db.inTransaction()) db.endTransaction();
                    if (db.isOpen()) db.close();
                    db = null;
                } catch (IllegalStateException | NullPointerException e) {
                    e.printStackTrace();
                }
            }
            return (count == 0);
        }
    }

    /**
     * Increments the internal db counter by one and opens the db if needed.
     *
     * @return db SQLiteDatabase object which is readable and writable
     */
    @CallSuper
    protected SQLiteDatabase open() {
        synchronized (managerLockObject) {
            int count = sqLiteOpenHelper.addConnection();
            //Log.w("LockCount++", "" + count);
            if (db == null || !db.isOpen()) {
                db = sqLiteOpenHelper.getWritableDatabase();
            }
            return db;
        }
    }

    /**
     * Perform database operations in one transaction.
     * @param transactionFunc Pass the function as callable interface
     * @return true if successful, else false
     * @throws SQLException
     */
    @CallSuper
    public boolean doTransaction(Callable<Boolean> transactionFunc) throws SQLException {
        boolean successful = false;
        /*if (db != null && db.inTransaction()) {
            throw new SQLException("Database already in transaction");
        }*/
        SQLiteDatabase database = this.open();
        database.beginTransaction();

        try {
            boolean commit = transactionFunc.call();
            if (commit) {
                database.setTransactionSuccessful();
                successful = true;
            }
        } catch (Exception e) {
            e.printStackTrace();
            throw new SQLException("Transaction failed");
        } finally {
            database.endTransaction();
            this.close();
        }
        return successful;
    }

    /**
     * Insert a row into the database
     * @param tableName the table to insert the row into
     * @param initialValues this map contains the initial column values for the
     *            row. The keys should be the column names and the values the
     *            column values
     * @return row ID if successful, else -1
     * @throws SQLException
     */
    @CallSuper
    public long insert(String tableName, ContentValues initialValues) throws SQLException {
        return this.insertWithOnConflict(tableName, initialValues, SQLiteDatabase.CONFLICT_NONE);
    }

    /**
     * Insert a row into the database
     * @param tableName the table to insert the row into
     * @param initialValues this map contains the initial column values for the
     *            row. The keys should be the column names and the values the
     *            column values
     * @param conflictAlgorithm for insert conflict resolver
     * @return row ID if successful, else -1
     * @throws SQLException
     */
    @CallSuper
    public long insertWithOnConflict(String tableName, ContentValues initialValues, int conflictAlgorithm) throws SQLException {
        long rowId = -1;
        SQLiteDatabase database;
        boolean inTransaction = false;
        if (this.inTransaction()) {
            database = db;
            inTransaction = true;
        } else {
            database = this.open();
        }
        try {
            if (initialValues == null) {
                throw new SQLException("Can not create empty row");
            }

            rowId = database.insertWithOnConflict(tableName, null, initialValues, conflictAlgorithm);

        } finally {
            if (!inTransaction) {
                this.close();
            }
        }
        return rowId;
    }

    /**
     * Insert multiple rows at once as a single transaction
     * @param tableName the table to update in
     * @param values a map from column names to new column values. null is a
     *            valid value that will be translated to NULL.
     * @return number of rows inserted
     * @throws SQLException
     */
    @CallSuper
    public int bulkInsert(String tableName, ContentValues[] values) throws SQLException {
        //Long now = Long.valueOf(System.currentTimeMillis());
        // Make sure that the fields are all set
        /*if (db!=null && db.inTransaction()) {
            throw new SQLException("Database already in transaction");
        }*/

        int numInserted = 0;
        SQLiteDatabase database = this.open();
        database.beginTransaction();
        try {
            for (ContentValues cv : values) {
                database.insertOrThrow(tableName, null, cv);
            }
            database.setTransactionSuccessful();
            numInserted = values.length;
        } finally {
            database.endTransaction();
            this.close();
        }
        return numInserted;
    }

    /**
     * Delete from database
     * @param tableName the table to update in
     * @param selection the optional WHERE clause to apply when deleting.
     *            Passing null will update all rows.
     * @param selectionArgs You may include ?s in the where clause, which
     *            will be replaced by the values from whereArgs. The values
     *            will be bound as Strings.
     * @return number of deleted rows
     * @throws SQLException
     */
    @CallSuper
    public int delete(String tableName, String selection, String[] selectionArgs) throws SQLException {
        SQLiteDatabase database;
        boolean inTransaction = false;
        if (this.inTransaction()) {
            database = db;
            inTransaction = true;
        } else {
            database = this.open();
        }
        int count;
        try {
            count = database.delete(tableName, selection, selectionArgs);
            if (count < 0) {
                throw new SQLException("Can not delete from " + tableName);
            }
        } finally {
            if (!inTransaction) {
                this.close();
            }
        }
        return count;
    }

    /**
     * Update rows in the database
     * @param tableName the table to update in
     * @param values a map from column names to new column values. null is a
     *            valid value that will be translated to NULL.
     * @param selection the optional WHERE clause to apply when updating.
     *            Passing null will update all rows.
     * @param selectionArgs You may include ?s in the where clause, which
     *            will be replaced by the values from whereArgs. The values
     *            will be bound as Strings.
     * @return number of updated rows
     * @throws SQLException
     */
    @CallSuper
    public int update(String tableName, ContentValues values, String selection, String[] selectionArgs) throws SQLException {
        return this.updateWithOnConflict(tableName, values, selection, selectionArgs, SQLiteDatabase.CONFLICT_NONE);
    }

    /**
     * Update rows in the database
     * @param tableName the table to update in
     * @param values a map from column names to new column values. null is a
     *            valid value that will be translated to NULL.
     * @param selection the optional WHERE clause to apply when updating.
     *            Passing null will update all rows.
     * @param selectionArgs You may include ?s in the where clause, which
     *            will be replaced by the values from whereArgs. The values
     *            will be bound as Strings.
     * @param conflictAlgorithm for update conflict resolver
     * @return number of rows updated
     * @throws SQLException
     */
    @CallSuper
    public int updateWithOnConflict(String tableName, ContentValues values, String selection, String[] selectionArgs, int conflictAlgorithm) throws SQLException {
        SQLiteDatabase database;
        boolean inTransaction = false;
        if (this.inTransaction()) {
            database = db;
            inTransaction = true;
        } else {
            database = this.open();
        }
        int count;
        try {
            count = database.updateWithOnConflict(tableName, values, selection, selectionArgs, conflictAlgorithm);
            if (count < 0) {
                throw new SQLException("Can not update " + tableName);
            }
        } finally {
            if (!inTransaction) {
                this.close();
            }
        }
        return count;
    }

    /**
     * Query the database
     * @param tableNames tables to query
     * @param projection A list of which columns to return. Passing
     *   null will return all columns, which is discouraged to prevent
     *   reading data from storage that isn't going to be used.
     * @param selection A filter declaring which rows to return,
     *   formatted as an SQL WHERE clause (excluding the WHERE
     *   itself). Passing null will return all rows for the given URL.
     * @param selectionArgs You may include ?s in selection, which
     *   will be replaced by the values from selectionArgs, in order
     *   that they appear in the selection. The values will be bound
     *   as Strings.
     * @param sortOrder How to order the rows, formatted as an SQL
     *   ORDER BY clause (excluding the ORDER BY itself). Passing null
     *   will use the default sort order, which may be unordered.
     * @return Cursor
     */
    @CallSuper
    public Cursor query(String tableNames, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        return this.query(tableNames, projection, selection, selectionArgs, null, null, sortOrder, null);
    }

    /**
     * Query the database
     * @param tableNames tables to query
     * @param projection A list of which columns to return. Passing
     *   null will return all columns, which is discouraged to prevent
     *   reading data from storage that isn't going to be used.
     * @param selection A filter declaring which rows to return,
     *   formatted as an SQL WHERE clause (excluding the WHERE
     *   itself). Passing null will return all rows for the given URL.
     * @param selectionArgs You may include ?s in selection, which
     *   will be replaced by the values from selectionArgs, in order
     *   that they appear in the selection. The values will be bound
     *   as Strings.
     * @param groupBy A filter declaring how to group rows, formatted
     *   as an SQL GROUP BY clause (excluding the GROUP BY
     *   itself). Passing null will cause the rows to not be grouped.
     * @param having A filter declare which row groups to include in
     *   the cursor, if row grouping is being used, formatted as an
     *   SQL HAVING clause (excluding the HAVING itself).  Passing
     *   null will cause all row groups to be included, and is
     *   required when row grouping is not being used.
     * @param sortOrder How to order the rows, formatted as an SQL
     *   ORDER BY clause (excluding the ORDER BY itself). Passing null
     *   will use the default sort order, which may be unordered.
     * @param limit Limits the number of rows returned by the query,
     *   formatted as LIMIT clause. Passing null denotes no LIMIT clause.
     * @return Cursor
     */
    @CallSuper
    public Cursor query(String tableNames, String[] projection, String selection, String[] selectionArgs, String groupBy,
                        String having, String sortOrder, String limit) {
        SQLiteDatabase database;
        boolean inTransaction = false;
        if (this.inTransaction()) {
            database = db;
            inTransaction = true;
        } else {
            database = this.open();
        }
        SQLiteQueryBuilder qb = new SQLiteQueryBuilder();
        qb.setTables(tableNames);
        if (!inTransaction) {
            return new DbManCursor(qb.query(database, projection, selection, selectionArgs, groupBy, having, sortOrder, limit), this);
        } else {
            return qb.query(database, projection, selection, selectionArgs, groupBy, having, sortOrder, limit);
        }
    }

    /**
     * Query the database using a raw query
     * @param sql the SQL query. The SQL string must not be ; terminated
     * @param selectionArgs You may include ?s in where clause in the query,
     *     which will be replaced by the values from selectionArgs. The
     *     values will be bound as Strings.
     * @return Cursor
     */
    @CallSuper
    public Cursor rawQuery(String sql, String[] selectionArgs) {
        SQLiteDatabase database;
        boolean inTransaction = false;
        if (this.inTransaction()) {
            database = db;
            inTransaction = true;
        } else {
            database = this.open();
        }
        if (!inTransaction) {
            return new DbManCursor(database.rawQuery(sql, selectionArgs), this);
        } else {
            return database.rawQuery(sql, selectionArgs);
        }
    }
}