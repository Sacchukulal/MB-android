package com.magicbill.app

import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.magicbill.app.core.MbJson
import com.magicbill.app.db.MbDatabase
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE, application = android.app.Application::class)
class FloorMigrationTest {
    @Test fun version_five_orders_keep_their_open_dependencies_and_room_validates_the_upgrade() = runTest {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val name = MbDatabase.NAME
        context.deleteDatabase(name)
        val schema = MbJson.parseToJsonElement(File("schemas/${MbDatabase::class.java.name}/5.json").readText()).jsonObject["database"]!!.jsonObject
        val helper = FrameworkSQLiteOpenHelperFactory().create(SupportSQLiteOpenHelper.Configuration.builder(context).name(name)
            .callback(object : SupportSQLiteOpenHelper.Callback(5) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    for (entry in schema["entities"]!!.jsonArray) {
                        val entity = entry.jsonObject
                        val table = entity["tableName"]!!.jsonPrimitive.content
                        fun sql(value: String) = value.replace("\${TABLE_NAME}", table)
                        db.execSQL(sql(entity["createSql"]!!.jsonPrimitive.content))
                        for (index in entity["indices"]?.jsonArray.orEmpty()) db.execSQL(sql(index.jsonObject["createSql"]!!.jsonPrimitive.content))
                    }
                    // Two separate groups at the same timestamp, including an already answered open.
                    for ((id, action, state) in listOf(Triple("open1", "open_order", "ok"), Triple("dish1", "add_item", "queued"), Triple("open2", "open_order", "queued"), Triple("dish2", "add_item", "queued"))) {
                        db.execSQL("INSERT INTO intents (id,orderId,atMs,what,label,tableLabel,state,outcome,createdMs,answeredMs,attempts) VALUES (?,NULL,100,?,'test','1',?,NULL,100,NULL,0)", arrayOf(id, "{\"do\":\"$action\"}", state))
                    }
                    db.execSQL("INSERT INTO floor_tables VALUES ('t1','1','Hall',4,'taken',0)")
                }
                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
            }).build())
        helper.writableDatabase
        helper.close()
        val db = MbDatabase.open(context)
        try {
            assertEquals("open1", db.intents().byId("dish1")!!.openIntentId)
            assertEquals("open2", db.intents().byId("dish2")!!.openIntentId)
            assertEquals("ok", db.intents().byId("open1")!!.state)
            assertEquals(1, db.openHelper.readableDatabase.query("SELECT * FROM floor_tables").use { it.count })
            assertNull(db.floor().order("missing"))
        } finally {
            db.close()
            context.deleteDatabase(name)
        }
    }
}
