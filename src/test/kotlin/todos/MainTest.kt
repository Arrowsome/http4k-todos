package todos

import org.http4k.core.HttpHandler
import org.http4k.core.Method.*
import org.http4k.core.Request
import org.http4k.core.Status.Companion.CREATED
import org.http4k.core.Status.Companion.NO_CONTENT
import org.http4k.core.Status.Companion.OK
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class MainTest {
    private lateinit var _backend: HttpHandler

    @BeforeEach
    fun setup() {
        _backend = backend
        todosDatabase.get().clear()
    }

    @Test
    fun `create a todo with only a title`() {
        val todo = simpleTodos.first()
        val request = todoLens.inject(todo, Request(POST, "/todos"))
        val response = _backend(request)

        assertEquals(CREATED, response.status)
        assertEquals(todo, todoLens.extract(response))
        assertEquals(todosDatabase.get().size, 1)
    }

    @Test
    fun `create a todo with everything set`() {
        val request = todoLens.inject(complexTodo, Request(POST, "/todos"))
        val response = _backend(request)

        assertEquals(CREATED, response.status)
        assertEquals(complexTodo, todoLens.extract(response))
        assertEquals(todosDatabase.get().size, 1)
    }

    @Test
    fun `get list of all todos is empty`() {
        val request = _backend(Request(GET, "/todos"))
        assertEquals(NO_CONTENT, request.status)
        assertEquals(0, todosDatabase.get().size)
    }

    @Test
    fun `get two batch of todos id cursor based default (desc) order`() {
        for (todo in simpleTodos)
            _backend(todoLens.inject(todo, Request(POST, "/todos")))

        val response01 = _backend(Request(GET, "/todos?limit=5"))
        val todos01 = todosLens.extract(response01)

        assertEquals(OK, response01.status)
        assertEquals(
            simpleTodos.last().id,
            todos01.first().id,
        )
        assertEquals(
            simpleTodos[simpleTodos.lastIndex - 4].id,
            todos01.last().id,
        )

        val cursorId = todos01.last().id

        val response02 = _backend(Request(GET, "/todos?limit=5&cursor_id=$cursorId"))
        val todos02 = todosLens.extract(response02)

        assertEquals(OK, response01.status)
        assertEquals(
            simpleTodos[9].id,
            todos02.first().id,
        )
        assertEquals(
            simpleTodos[5].id,
            todos02.last().id,
        )
    }

    @Test
    fun `get more than 100 items in one request leads to bad request`() {
        val exc = assertThrows<IllegalArgumentException> {
            _backend(Request(GET, "/todos?limit=101"))
        }

        assertNotNull(exc)
        assertTrue(exc.message == "The provided 101 limit is more than acceptable value 100.")
    }

    @Test
    fun `get with an non existing cursor id leads to bad request`() {
        val exc = assertThrows<IllegalArgumentException> {
            _backend(Request(GET, "/todos?cursor_id=1234"))
        }

        assertNotNull(exc)
        assertTrue(exc.message == "The provided 1234 cursor id was not found!")
    }

    @Test
    fun `get full collection of todos with a more than existing todos size`() {
        for (todo in simpleTodos)
            _backend(todoLens.inject(todo, Request(POST, "/todos")))

        val response01 = _backend(Request(GET, "/todos?limit=20"))
        val todos = todosLens.extract(response01)

        assertEquals(simpleTodos.size, todos.size)
    }

    @Test
    fun `delete todo from db is success`() {
        for (todo in simpleTodos.subList(0, 5))
            _backend(todoLens.inject(todo, Request(POST, "/todos")))

        val response01 = _backend(Request(DELETE, "/todos/${simpleTodos.first().id}"))

        assertEquals(NO_CONTENT, response01.status)

        val response02 = _backend(Request(GET, "/todos"))
        val todos = todosLens.extract(response02)

        assertEquals(OK, response02.status)
        assertEquals(4, todos.size)
        assertNotEquals(simpleTodos.first(), todos.first())
    }

    @Test
    fun `delete todo with non existing id is not found`() {
        val exc = assertThrows<IllegalArgumentException> {
            _backend(Request(DELETE, "/todos/1234"))

        }
        assertNotNull(exc)
        assertTrue(exc.message == "The provided 1234 id was not found!")
    }

    @Test
    fun `update todo is a success`() {
        for (todo in simpleTodos.subList(0, 5))
            _backend(todoLens.inject(todo, Request(POST, "/todos")))

        val updatedTodo = simpleTodos.first().copy(title = "Task Updated #01")
        val updateResponse = _backend(todoLens.inject(updatedTodo, Request(PUT, "/todos")))

        assertEquals(OK, updateResponse.status)
        assertEquals(updatedTodo.title, todoLens.extract(updateResponse).title)

        val todosResponse = _backend(Request(GET, "/todos?sort=asc"))
        val todos = todosLens.extract(todosResponse)

        assertEquals(updatedTodo.title, todos.first().title)
        assertEquals(5, todos.size)
    }

    @Test
    fun `update a non existing todo throws exception`() {
        val exc = assertThrows<IllegalArgumentException> {
            val updatedTodo = simpleTodos.first().copy(title = "Task Updated #01")
            _backend(todoLens.inject(updatedTodo, Request(PUT, "/todos")))
        }

        assertNotNull(exc)
        assertTrue(exc.message == "The provided ${simpleTodos.first().id} id was not found!")
    }

    @Test
    fun `get a single todo successfully`() {
        for (todo in simpleTodos.subList(0, 5))
            _backend(todoLens.inject(todo, Request(POST, "/todos")))

        val targetId = simpleTodos.first().id

        val todoResponse = _backend(Request(GET, "/todos/$targetId"))
        val todo = todoLens.extract(todoResponse)

        assertEquals(OK, todoResponse.status)
        assertEquals(simpleTodos.first(), todo)
    }

    @Test
    fun `get a non existing todo fails`() {
        val targetId = simpleTodos.first().id
        val exc = assertThrows<IllegalArgumentException> {
            val todoResponse = _backend(Request(GET, "/todos/$targetId"))
        }

        assertNotNull(exc)
        assertTrue(exc.message == "The provided $targetId id has no todo result!")
    }

    companion object {
        private val complexTodo = Todo(
            title = "Meet Nina for Launch",
            description = "Don't forget to buy flowers before meeting her",
            tag = listOf("event", "nina"),
        )

        private val simpleTodos = listOf(
            Todo(title = "Task #01"),
            Todo(title = "Task #02"),
            Todo(title = "Task #03"),
            Todo(title = "Task #04"),
            Todo(title = "Task #05"),

            Todo(title = "Task #06"),
            Todo(title = "Task #07"),
            Todo(title = "Task #08"),
            Todo(title = "Task #09"),
            Todo(title = "Task #10"),

            Todo(title = "Task #11"),
            Todo(title = "Task #12"),
            Todo(title = "Task #13"),
            Todo(title = "Task #14"),
            Todo(title = "Task #15"),
        )
    }

}
