/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package mozilla.components.browser.storage.sync

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import mozilla.appservices.places.BookmarkFolder
import mozilla.appservices.places.BookmarkItem
import mozilla.appservices.places.BookmarkRoot
import mozilla.appservices.places.BookmarkSeparator
import mozilla.appservices.places.BookmarkTreeNode
import mozilla.appservices.places.BookmarkUpdateInfo
import mozilla.appservices.places.PlacesReaderConnection
import mozilla.appservices.places.PlacesWriterConnection
import mozilla.components.concept.storage.BookmarkInfo
import mozilla.components.concept.storage.BookmarkNode
import mozilla.components.concept.storage.BookmarkNodeType
import mozilla.components.concept.sync.SyncAuthInfo
import mozilla.components.support.test.mock
import mozilla.components.support.test.robolectric.testContext
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.`when`
import org.mockito.Mockito.doNothing
import org.mockito.Mockito.times
import org.mockito.Mockito.verify

@RunWith(AndroidJUnit4::class)
class PlacesBookmarksStorageTest {
    private var conn: Connection? = null
    private var reader: PlacesReaderConnection? = null
    private var writer: PlacesWriterConnection? = null
    private var authInfo: SyncAuthInfo? = null

    private var storage: PlacesBookmarksStorage? = null

    private val handle: Long = 33L
    private val newItem = BookmarkNode(BookmarkNodeType.ITEM, "123", "456", 12,
            "Mozilla", "http://www.mozilla.org", null)
    private val newFolder = BookmarkNode(BookmarkNodeType.FOLDER, "789", "321", 12,
            "Cool Sites", null, listOf())
    private val newSeparator = BookmarkNode(BookmarkNodeType.SEPARATOR, "654", "987",
            12, null, null, null)

    private val newItemTreeNode = BookmarkItem(newItem.guid, 0L, 1L,
            newItem.parentGuid, newItem.position!!, newItem.url!!, newItem.title!!)
    private val newFolderTreeNode = BookmarkFolder(newFolder.guid, 0L, 1L,
            newFolder.parentGuid, newFolder.position!!, newFolder.title!!, mutableListOf("444"), null)
    private val newSeparatorTreeNode = BookmarkSeparator(newSeparator.guid, 0L, 1L,
            newSeparator.parentGuid, newSeparator.position!!)

    internal class TestablePlacesBookmarksStorage(override val places: Connection) : PlacesBookmarksStorage(testContext)

    @Before
    fun setup() {
        conn = mock()
        reader = mock()
        writer = mock()
        authInfo = mock()

        `when`(conn!!.reader()).thenReturn(reader)
        `when`(conn!!.writer()).thenReturn(writer)

        storage = TestablePlacesBookmarksStorage(conn!!)
    }

    @Test
    fun `get bookmarks tree by root, recursive or not`() {
        val reader = reader!!
        val storage = storage!!

        runBlocking {
            storage.getTree(BookmarkRoot.Root.id)
        }
        verify(reader, times(1)).getBookmarksTree(BookmarkRoot.Root.id, false)

        runBlocking {
            storage.getTree(BookmarkRoot.Root.id, true)
        }
        verify(reader, times(1)).getBookmarksTree(BookmarkRoot.Root.id, true)
    }

    @Test
    fun `get bookmarks by URL`() {
        val reader = reader!!
        val storage = storage!!

        val url = "http://www.mozilla.org"

        runBlocking {
            storage.getBookmarksWithUrl(url)
        }
        verify(reader, times(1)).getBookmarksWithURL(url)
    }

    @Test
    fun `get bookmark by guid`() {
        val reader = reader!!
        val storage = storage!!

        val guid = "123"

        runBlocking {
            storage.getBookmark(guid)
        }
        verify(reader, times(1)).getBookmark(guid)
    }

    @Test
    fun `search bookmarks by keyword`() {
        val reader = reader!!
        val storage = storage!!

        runBlocking {
            storage.searchBookmarks("mozilla")
        }
        verify(reader, times(1)).searchBookmarks("mozilla", 10)

        runBlocking {
            storage.searchBookmarks("mozilla", 30)
        }
        verify(reader, times(1)).searchBookmarks("mozilla", 30)
    }

    @Test
    fun `add a bookmark item`() {
        val writer = writer!!
        val storage = storage!!

        runBlocking {
            storage.addItem(BookmarkRoot.Mobile.id, newItem.url!!, newItem.title!!, null)
        }
        verify(writer, times(1)).createBookmarkItem(
                BookmarkRoot.Mobile.id, "http://www.mozilla.org", "Mozilla", null)

        runBlocking {
            storage.addItem(BookmarkRoot.Mobile.id, newItem.url!!, newItem.title!!, 3)
        }
        verify(writer, times(1)).createBookmarkItem(
                BookmarkRoot.Mobile.id, "http://www.mozilla.org", "Mozilla", 3)
    }

    @Test
    fun `add a bookmark folder`() {
        val writer = writer!!
        val storage = storage!!

        runBlocking {
            storage.addFolder(BookmarkRoot.Mobile.id, newFolder.title!!, null)
        }
        verify(writer, times(1)).createFolder(
                BookmarkRoot.Mobile.id, "Cool Sites", null)

        runBlocking {
            storage.addFolder(BookmarkRoot.Mobile.id, newFolder.title!!, 4)
        }
        verify(writer, times(1)).createFolder(
                BookmarkRoot.Mobile.id, "Cool Sites", 4)
    }

    @Test
    fun `add a bookmark separator`() {
        val writer = writer!!
        val storage = storage!!

        runBlocking {
            storage.addSeparator(BookmarkRoot.Mobile.id, null)
        }
        verify(writer, times(1)).createSeparator(
                BookmarkRoot.Mobile.id, null)

        runBlocking {
            storage.addSeparator(BookmarkRoot.Mobile.id, 4)
        }
        verify(writer, times(1)).createSeparator(
                BookmarkRoot.Mobile.id, 4)
    }

    @Test
    fun `move a bookmark item`() {
        val writer = writer!!
        val storage = storage!!
        val info = BookmarkInfo(newItem.parentGuid, newItem.position, newItem.title, newItem.url)

        runBlocking {
            storage.updateNode(BookmarkRoot.Mobile.id, info)
        }
        verify(writer, times(1)).updateBookmark(
                BookmarkRoot.Mobile.id, info.asBookmarkUpdateInfo())

        runBlocking {
            storage.updateNode(BookmarkRoot.Mobile.id, info.copy(position = 4))
        }
        verify(writer, times(1)).updateBookmark(
                BookmarkRoot.Mobile.id, info.copy(position = 4).asBookmarkUpdateInfo())
    }

    @Test
    fun `move a bookmark folder and its contents`() {
        val writer = writer!!
        val storage = storage!!
        val info = BookmarkInfo(newFolder.parentGuid, newFolder.position, newFolder.title, newFolder.url)

        runBlocking {
            storage.updateNode(BookmarkRoot.Mobile.id, info)
        }
        verify(writer, times(1)).updateBookmark(
                BookmarkRoot.Mobile.id, info.asBookmarkUpdateInfo()
        )
        runBlocking {
            storage.updateNode(BookmarkRoot.Mobile.id, info.copy(position = 5))
        }
        verify(writer, times(1)).updateBookmark(
                BookmarkRoot.Mobile.id, info.copy(position = 5).asBookmarkUpdateInfo()
        )
    }

    @Test
    fun `move a bookmark separator`() {
        val writer = writer!!
        val storage = storage!!
        val info = BookmarkInfo(newSeparator.parentGuid, newSeparator.position, newSeparator.title, newSeparator.url)

        runBlocking {
            storage.updateNode(BookmarkRoot.Mobile.id, info)
        }
        verify(writer, times(1)).updateBookmark(
                BookmarkRoot.Mobile.id, info.asBookmarkUpdateInfo()
        )
        runBlocking {
            storage.updateNode(BookmarkRoot.Mobile.id, info.copy(position = 6))
        }
        verify(writer, times(1)).updateBookmark(BookmarkRoot.Mobile.id, info.copy(position = 6).asBookmarkUpdateInfo())
    }

    @Test
    fun `update a bookmark item`() {
        val writer = writer!!
        val storage = storage!!

        val info = BookmarkInfo("121", 1, "Firefox", "https://www.mozilla.org/en-US/firefox/")

        runBlocking {
            storage.updateNode(newItem.guid, info)
        }
        verify(writer, times(1)).updateBookmark("123", info.asBookmarkUpdateInfo())
    }

    @Test
    fun `update a bookmark folder`() {
        val writer = writer!!
        val storage = storage!!

        val info = BookmarkInfo("131", 2, "Firefox", null)

        runBlocking {
            storage.updateNode(newFolder.guid, info)
        }
        verify(writer, times(1)).updateBookmark(newFolder.guid, info.asBookmarkUpdateInfo())
    }

    @Test
    fun `delete a bookmark item`() {
        val writer = writer!!
        val storage = storage!!

        runBlocking {
            storage.deleteNode(newItem.guid)
        }
        verify(writer, times(1)).deleteBookmarkNode(newItem.guid)
    }

    @Test
    fun `delete a bookmark separator`() {
        val writer = writer!!
        val storage = storage!!

        runBlocking {
            storage.deleteNode(newSeparator.guid)
        }
        verify(writer, times(1)).deleteBookmarkNode(newSeparator.guid)
    }

    @Test
    fun `sync bookmarks`() {
        val authInfo = authInfo!!
        val storage = storage!!
        val conn = conn!!

        doNothing().`when`(conn).syncBookmarks(authInfo)

        runBlocking {
            storage.sync(authInfo)
        }
        verify(conn, times(1)).syncBookmarks(authInfo)
    }

    @Test
    fun `get handle`() {
        val storage = storage!!
        val conn = conn!!

        `when`(conn.getHandle()).thenReturn(handle)

        var returnValue = 0L
        runBlocking {
            returnValue = storage.getHandle()
        }

        assertEquals(handle, returnValue)
        verify(conn, times(1)).getHandle()
    }

    @Test
    fun `able to convert tree nodes`() {
        val storage = storage!!

        var bookmarkNode: BookmarkNode = storage.asBookmarkNode(newItemTreeNode)!!
        assertBookmarkNodeValues(newItemTreeNode, bookmarkNode)
        assertEquals(newItemTreeNode.title, bookmarkNode.title)
        assertEquals(newItemTreeNode.url, bookmarkNode.url)

        bookmarkNode = storage.asBookmarkNode(newFolderTreeNode)!!
        assertBookmarkNodeValues(newFolderTreeNode, bookmarkNode)
        assertEquals(newFolderTreeNode.title, bookmarkNode.title)
        assertEquals(null, bookmarkNode.url)

        bookmarkNode = storage.asBookmarkNode(newSeparatorTreeNode)!!
        assertBookmarkNodeValues(newSeparatorTreeNode, bookmarkNode)
        assertEquals(null, bookmarkNode.title)
        assertEquals(null, bookmarkNode.url)
    }

    private fun assertBookmarkNodeValues(expectedTreeNode: BookmarkTreeNode, bookmarkNode: BookmarkNode) {
        assertEquals(expectedTreeNode.guid, bookmarkNode.guid)
        assertEquals(expectedTreeNode.position, bookmarkNode.position)
        assertEquals(expectedTreeNode.parentGUID, bookmarkNode.parentGuid)
    }

    private fun BookmarkInfo.asBookmarkUpdateInfo(): BookmarkUpdateInfo = BookmarkUpdateInfo(this.parentGuid, this.position, this.title, this.url)
}
