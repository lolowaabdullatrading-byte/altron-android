package qa.altron.app

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import org.json.JSONArray
import org.json.JSONObject

/** Offline buffer for GPS points (sites often have no signal). Uploaded in batches, oldest first, capped at 3000 rows. */
class LocationQueue(ctx: Context) : SQLiteOpenHelper(ctx, "altron_track.db", null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE pts (id INTEGER PRIMARY KEY AUTOINCREMENT, lat REAL, lng REAL, acc INTEGER, spd REAL, ts INTEGER)")
    }
    override fun onUpgrade(db: SQLiteDatabase, o: Int, n: Int) {}

    fun add(lat: Double, lng: Double, acc: Int, spd: Float, ts: Long) {
        writableDatabase.execSQL("INSERT INTO pts (lat,lng,acc,spd,ts) VALUES (?,?,?,?,?)", arrayOf(lat, lng, acc, spd, ts))
        writableDatabase.execSQL("DELETE FROM pts WHERE id NOT IN (SELECT id FROM pts ORDER BY id DESC LIMIT 3000)")
    }
    fun count(): Int = readableDatabase.rawQuery("SELECT COUNT(*) FROM pts", null).use { it.moveToFirst(); it.getInt(0) }
    /** Returns up to [limit] oldest points and their max id. */
    fun batch(limit: Int = 150): Pair<JSONArray, Long> {
        val arr = JSONArray(); var maxId = 0L
        readableDatabase.rawQuery("SELECT id,lat,lng,acc,spd,ts FROM pts ORDER BY id LIMIT $limit", null).use { c ->
            while (c.moveToNext()) {
                maxId = c.getLong(0)
                arr.put(JSONObject().put("lat", c.getDouble(1)).put("lng", c.getDouble(2)).put("acc", c.getInt(3)).put("spd", c.getDouble(4)).put("ts", c.getLong(5)))
            }
        }
        return Pair(arr, maxId)
    }
    fun deleteUpTo(maxId: Long) { writableDatabase.execSQL("DELETE FROM pts WHERE id <= ?", arrayOf(maxId)) }
    fun clear() { writableDatabase.execSQL("DELETE FROM pts") }
}
