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
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteQueryBuilder;
import androidx.annotation.CallSuper;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.CipherOutputStream;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.SecretKeySpec;

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

        DBSQLiteOpenHelper(Context context, String name, int version, DbManPlus dbManPlus) {
            super(context, name, null, version);
            this.dbManPlus = dbManPlus;
        }

        int addConnection() {
            return counter.incrementAndGet();
        }

        int removeConnection() {
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

    private static final Object managerLockObject = new Object();


    private DBSQLiteOpenHelper sqLiteOpenHelper;
    private SQLiteDatabase db;

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
            }
            //SQLiteOpenHelper class caches the SQLiteDatabase, so this will be the same SQLiteDatabase object every time
            db = sqLiteOpenHelper.getWritableDatabase();
        }
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
     * Lowers the DB counter by 1 for any {@link DbManPlus}s referencing the same DB on disk
     * <br />If the new counter is 0, then the database will be closed.
     * <br /><br />This needs to be called before application exit.
     *
     * @return true if the underlying {@link android.database.sqlite.SQLiteDatabase} is closed (counter is 0), and false otherwise (counter > 0)
     */
    protected boolean close() {
        synchronized (managerLockObject) {
            int count = sqLiteOpenHelper.removeConnection();
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
     * @throws Exception
     */
    @CallSuper
    public boolean doTransaction(Callable<Boolean> transactionFunc) throws Exception {
        boolean successful = false;
        SQLiteDatabase database = this.open();
        database.beginTransaction();

        try {
            boolean commit = transactionFunc.call();
            if (commit) {
                database.setTransactionSuccessful();
                successful = true;
            }
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
     */
    @CallSuper
    public long insert(String tableName, ContentValues initialValues) {
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
     */
    @CallSuper
    public long insertWithOnConflict(String tableName, ContentValues initialValues, int conflictAlgorithm) {
        long rowId = -1;
        SQLiteDatabase database = this.open();
        try {
            rowId = database.insertWithOnConflict(tableName, null, initialValues, conflictAlgorithm);

        } finally {
            this.close();
        }
        return rowId;
    }

    /**
     * Insert multiple rows at once as a single transaction
     * @param tableName the table to update in
     * @param values a map from column names to new column values. null is a
     *            valid value that will be translated to NULL.
     * @return number of rows inserted
     */
    @CallSuper
    public int bulkInsert(String tableName, ContentValues[] values) {
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
     */
    @CallSuper
    public int delete(String tableName, String selection, String[] selectionArgs) {
        SQLiteDatabase database = this.open();

        int count;
        try {
            count = database.delete(tableName, selection, selectionArgs);
        } finally {
            this.close();
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
     */
    @CallSuper
    public int update(String tableName, ContentValues values, String selection, String[] selectionArgs) {
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
     */
    @CallSuper
    public int updateWithOnConflict(String tableName, ContentValues values, String selection, String[] selectionArgs, int conflictAlgorithm) {
        SQLiteDatabase database = this.open();
        int count;
        try {
            count = database.updateWithOnConflict(tableName, values, selection, selectionArgs, conflictAlgorithm);
        } finally {
            this.close();
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
        SQLiteDatabase database = this.open();

        SQLiteQueryBuilder qb = new SQLiteQueryBuilder();
        qb.setTables(tableNames);
        return new DbCursor(qb.query(database, projection, selection, selectionArgs, groupBy, having, sortOrder, limit), this);
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
        SQLiteDatabase database = this.open();
        return new DbCursor(database.rawQuery(sql, selectionArgs), this);
    }

    /**
     * Export this database
     * @param backupPath external path where to export
     * @param salt encryption salt
     * @param password encryption password
     * @return database size
     * @throws IOException
     * @throws NoSuchAlgorithmException
     * @throws NoSuchPaddingException
     * @throws InvalidKeyException
     */
    public long exportDB(String backupPath, String salt, String password) throws IOException, NoSuchAlgorithmException, NoSuchPaddingException, InvalidKeyException {
        long transferred = 0;

        //File sd = Environment.getExternalStorageDirectory();

        String  currentDBPath= db.getPath();
        File currentDB = new File(currentDBPath);
        File backupDB = new File(backupPath);

        FileInputStream src;
        CipherOutputStream dst;
        db.beginTransaction();

        if(!backupDB.exists()) {
            backupDB.createNewFile();
        }

        byte[] key = (salt + password).getBytes("UTF-8");
        MessageDigest sha = MessageDigest.getInstance("SHA-1");
        key = sha.digest(key);
        key = Arrays.copyOf(key, 16); // use only first 128 bit
        SecretKeySpec secretKeySpec = new SecretKeySpec(key, "AES");

        Cipher cipher = Cipher.getInstance("AES");
        cipher.init(Cipher.ENCRYPT_MODE, secretKeySpec);

        src = new FileInputStream(currentDB);
        dst = new CipherOutputStream(new FileOutputStream(backupDB), cipher);

        byte[] buffer = new byte[4 * 1024];
        int read;

        while ((read = src.read(buffer)) != -1) {
                transferred += read;
                dst.write(buffer, 0, read);

        }
        dst.flush();

        db.endTransaction();

        src.close();
        dst.close();

        return transferred;
    }

    /**
     * Import to this database
     * @param backupStream stream to write to database (NOTE: stream is closed here so no need to close outside)
     * @param salt encryption salt
     * @param password encryption password
     * @return database size
     * @throws IOException
     * @throws NoSuchAlgorithmException
     * @throws NoSuchPaddingException
     * @throws InvalidKeyException
     */
    public long importDB(InputStream backupStream, String salt, String password) throws IOException, NoSuchAlgorithmException, NoSuchPaddingException, InvalidKeyException {
        long transferred = 0;

        String  currentDBPath= db.getPath();
        File currentDB  = new File(currentDBPath);
        FileOutputStream dst = null;

        db.beginTransaction();

        byte[] key = (salt + password).getBytes("UTF-8");
        MessageDigest sha = MessageDigest.getInstance("SHA-1");
        key = sha.digest(key);
        key = Arrays.copyOf(key, 16); // use only first 128 bit
        SecretKeySpec secretKeySpec = new SecretKeySpec(key, "AES");

        Cipher cipher = Cipher.getInstance("AES");
        cipher.init(Cipher.DECRYPT_MODE, secretKeySpec);

        CipherInputStream src = new CipherInputStream(backupStream, cipher);

        byte[] buffer = new byte[4 * 1024];
        int read;
        boolean isFirstBlock = true;
        boolean toContinue = true;

        while (toContinue && (read = src.read(buffer)) != -1) {
            if(isFirstBlock) {
                toContinue = isValidSQLiteDb(buffer);
                if(toContinue) {
                    dst = new FileOutputStream(currentDB);
                }
                isFirstBlock = false;
            }

            if (toContinue) {
                transferred += read;
                dst.write(buffer, 0, read);
            }
        }

        db.endTransaction();

        if (dst != null) {
            dst.flush();
            dst.close();
        }
        src.close();


        return transferred;
    }

    /**
     * Import to this database
     * @param backupPath path from which to read
     * @param salt encryption salt
     * @param password encryption password
     * @return database size
     * @throws IOException
     * @throws NoSuchAlgorithmException
     * @throws NoSuchPaddingException
     * @throws InvalidKeyException
     */
    public long importDB(String backupPath, String salt, String password) throws IOException, NoSuchAlgorithmException, NoSuchPaddingException, InvalidKeyException {
        File backupDb = new File(backupPath);
        FileInputStream backupStream = new FileInputStream(backupDb);
        return importDB(backupStream, salt, password);
    }

    /**
     * Check if database is valid SQLite 3 database (needs improvement to check schema)
     * @param buf buffer containing the first few bytes (at least 16)
     * @return true if database is valid
     */
    private boolean isValidSQLiteDb(byte[] buf) {
        ByteArrayOutputStream result = new ByteArrayOutputStream();
        result.write(buf, 0, 16);
        String str = result.toString();
        //Log.d("sqlite check", str);
        return str.equalsIgnoreCase("SQLite format 3\u0000");
    }
}