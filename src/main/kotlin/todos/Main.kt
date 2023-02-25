package todos

import org.http4k.core.*
import org.http4k.core.Method.GET
import org.http4k.core.Method.POST
import org.http4k.core.Method.DELETE
import org.http4k.core.Method.PUT
import org.http4k.core.Status.Companion.BAD_REQUEST
import org.http4k.core.Status.Companion.CREATED
import org.http4k.core.Status.Companion.NO_CONTENT
import org.http4k.core.Status.Companion.OK
import org.http4k.filter.DebuggingFilters
import org.http4k.format.Gson.auto
import org.http4k.lens.*
import org.http4k.routing.bind
import org.http4k.routing.routes
import org.http4k.server.SunHttp
import org.http4k.server.asServer
import java.util.*
import java.util.concurrent.atomic.AtomicReference


fun main() {
    DebuggingFilters.PrintRequestAndResponse()
        .then(exceptionFilter)
        .then(backend)
        .asServer(SunHttp(9000))
        .start()
}

val backend: HttpHandler
    get() = routes(
        "/" bind GET to {
            Response(OK).body("Hello, World!")
        },

        "/todos" bind POST to { req ->
            val todo = todoLens.extract(req)
            todosDatabase.get().add(todo)
            todoLens.inject(todo, Response(CREATED))
        },

        "/todos" bind GET to { req ->
            var todos = todosDatabase.get()
            val limit = limitLens.extract(req) ?: 10
            val cursorId = cursorIdLens.extract(req)
            val sort = sortLens.extract(req) ?: Sort.DESC

            if (limit > 100)
                throw IllegalArgumentException(
                    "The provided $limit limit is more than acceptable value 100."
                )

            if (sort == Sort.DESC)
                todos = todos
                    .reversed()
                    .toMutableList()

            val cursorIndex =
                if (cursorId != null) {
                    val index = todos.indexOfFirst { it.id == cursorId }
                    if (index == -1)
                        throw IllegalArgumentException("The provided $cursorId cursor id was not found!")
                    index
                }
                // TODO: no cursorId was found
                else
                    -1

            val from = cursorIndex + 1
            val to = from + limit
            val todosBatch = todos.filterIndexed { index, _ ->
                index in from until to
            }

            val isEmpty = todosBatch.isEmpty()

            if (isEmpty) {
                Response(NO_CONTENT)
            } else {
                todosLens.inject(todosBatch, Response(OK))
            }

        },

        "/todos/{id}" bind DELETE to { req ->
            val id = idLens.extract(req)
            val todos = todosDatabase.get()
            val index = todos.indexOfFirst { it.id == id }
            if (index == -1)
                throw IllegalArgumentException("The provided $id id was not found!")
            todos.removeAt(index)
            Response(NO_CONTENT)
        },

        "/todos" bind PUT to { req ->
            val todoToUpdate = todoLens.extract(req)
            val todos = todosDatabase.get()
            val index = todos.indexOfFirst { it.id == todoToUpdate.id }
            if (index == -1)
                throw IllegalArgumentException("The provided ${todoToUpdate.id} id was not found!")
            todos[index] = todoToUpdate
            todoLens.inject(todoToUpdate, Response(OK))
        },

        "/todos/{id}" bind GET to { req ->
            val id = idLens.extract(req)
            val todos = todosDatabase.get()
            val todo = todos.find { it.id == id }
                ?: throw IllegalArgumentException("The provided $id id has no todo result!")
            todoLens.inject(todo, Response(OK))
        }
    )

val todosDatabase = AtomicReference(mutableListOf<Todo>())

val todoLens = Body.auto<Todo>().toLens()
val todosLens = Body.auto<List<Todo>>().toLens()
val limitLens = Query.int().optional("limit")
val sortLens = Query.enum<Sort>(caseSensitive = false).optional("sort")
val cursorIdLens = Query.string().optional("cursor_id")
val idLens = Path.string().of("id")

val exceptionFilter: Filter = Filter { handler ->
    val wrapper: HttpHandler = { request ->
        try {
            handler(request)
        } catch (exc: IllegalArgumentException) {
            Response(BAD_REQUEST).body(exc.message.toString())
        }
    }

    return@Filter wrapper
}

enum class Sort { ASC, DESC }

data class Todo(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val description: String = "",
    val tag: List<String> = listOf(),
)