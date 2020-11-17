package com.jerry0022.pyco.shared

import android.os.Debug
import android.util.Log
import androidx.databinding.ObservableArrayList
import androidx.databinding.ObservableList
import com.couchbase.lite.*
import java.net.URI
import java.net.URISyntaxException
import kotlin.reflect.KClass


abstract class CouchbaseDatabaseRepresentation<T : PersistentObject> : ObservableArrayList<T>() {
    protected abstract val classToken : KClass<T>

    private val databaseName : String by lazy {
        if (classToken.simpleName.isNullOrBlank()) "standard" else classToken.simpleName + "_database"
    }

    /**
     * Ip address and port can be overwritten. If not, the general value is taken, see [CouchbaseDatabaseRepresentation.ipAddressAndPort].
     * E.g. "localhost" (=> Port 443)
     * E.g. "127.0.0.1" (=> Port 443)
     * E.g. "localhost:4444"
     * E.g. "127.0.0.1: 9999"
     */
    protected open var ipAddressAndPort : String = "206.81.31.63:4984"

    private lateinit var database : Database

    fun init()
    {
        // Reset database on debug mode
        if(!Debug.isDebuggerConnected()) {
            Database(databaseName).close()
            Database(databaseName).delete()
        }

        // Setup or read database
        val databaseConfig = DatabaseConfiguration()
        database = Database(databaseName, databaseConfig)
        val query: Query = QueryBuilder
                .select(SelectResult.all())
                .from(DataSource.database(database))
        addAll(query.execute().toObjectList(classToken.java))

        // Setup listener => this (=list) changes cause database changes
        var documentIDs = arrayListOf<String>()
        this.forEachIndexed{ index, element ->
            documentIDs.add(index, element.couchbaseID())
        }
        this.addOnListChangedCallback(object : ObservableList.OnListChangedCallback<CouchbaseDatabaseRepresentation<T>>() {
            override fun onChanged(sender: CouchbaseDatabaseRepresentation<T>?) {
                Log.w(this::class.simpleName, "Unknown change occured!")
            }

            override fun onItemRangeChanged(sender: CouchbaseDatabaseRepresentation<T>?, positionStart: Int, itemCount: Int) {
            }

            override fun onItemRangeInserted(sender: CouchbaseDatabaseRepresentation<T>?, positionStart: Int, itemCount: Int) {
                for (position in positionStart until positionStart + itemCount) {
                        database.save(sender!![position].getMutableDocument())
                        documentIDs.add(position, sender[position].couchbaseID())
                }
            }

            override fun onItemRangeMoved(sender: CouchbaseDatabaseRepresentation<T>?, fromPosition: Int, toPosition: Int, itemCount: Int) {
                documentIDs = arrayListOf()
                for(position in 0 until size)
                    documentIDs.add(documentIDs[position])
            }

            override fun onItemRangeRemoved(sender: CouchbaseDatabaseRepresentation<T>?, positionStart: Int, itemCount: Int) {
                for (position in positionStart until positionStart + itemCount) {
                    database.delete(database.getDocument(documentIDs[position]))
                    documentIDs.removeAt(position)
                }
            }
        })

        // Setup Replication to online server
        try {
            val endpoint: Endpoint = URLEndpoint(URI("wss://$ipAddressAndPort/getting-started-db"))
            val replicatorConfiguration = ReplicatorConfiguration(database, endpoint)
            replicatorConfiguration.setAuthenticator(BasicAuthenticator("user", "user".toCharArray()))
            val replicator = Replicator(replicatorConfiguration)
            replicator.start(true)
        } catch (e: URISyntaxException) {
            e.printStackTrace()
        }
    }

    /**
     * A needle searched in the hay.
     *
     * @param hay
     * @return Returns a list of the generic typed object.
     */
    protected infix fun String.searchIn(hay: String) : List<T>
    {
        val searchQuery = QueryBuilder
                .select(SelectResult.all())
                .from(DataSource.database(database))
                .where(Expression.property(hay).equalTo(Expression.string(this)))
        return searchQuery.execute().toObjectList(classToken.java)
    }


    /**
     * A needle searched in the hay.
     *
     * @param hay
     * @return Returns a list of the generic typed object.
     */
    protected infix fun String.searchFullTextIn(hay : String) : List<T>
    {
        if(!database.indexes.contains(hay)) throw IllegalArgumentException("Full text search not yet created for this hay. Call [createFullTextSearch()] first!")

        val searchQuery = QueryBuilder
                .select(SelectResult.all())
                .from(DataSource.database(database))
                .where(FullTextExpression.index(hay).match(this))
        return searchQuery.execute().toObjectList(classToken.java)
    }

    protected fun createFullTextSearch(hay: String, vararg fieldNames : String) = database.createIndex(hay, IndexBuilder.fullTextIndex(*fieldNames.map { FullTextIndexItem.property(it) }.toTypedArray()))

    protected fun deleteFullTextSearch(hay: String) = database.deleteIndex(hay)
}