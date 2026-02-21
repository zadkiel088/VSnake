package fr.eurecom.vsnake_test1

import android.graphics.Point

class Snake {
    private val body = mutableListOf<Point>()
    private var direction = Direction.RIGHT

    init {
        // serpent de base avec 3 segments
        body.add(Point(5, 5))
        body.add(Point(4, 5))
        body.add(Point(3, 5))
    }
    fun move() {
        val head = Point(body.first())
        when (direction) {
            Direction.UP -> head.y -= 1
            Direction.DOWN -> head.y += 1
            Direction.LEFT -> head.x -= 1
            Direction.RIGHT -> head.x += 1
        }
        body.add(0, head)
        body.removeLast()
    }
    fun grow() {
        val tail = Point(body.last())
        body.add(tail)
    }
    fun setDirection(newDir: Direction) {
        // empêcher demi-tour direct
        if ((direction == Direction.UP && newDir != Direction.DOWN) ||
            (direction == Direction.DOWN && newDir != Direction.UP) ||
            (direction == Direction.LEFT && newDir != Direction.RIGHT) ||
            (direction == Direction.RIGHT && newDir != Direction.LEFT)) {
            direction = newDir
        }
    }
    fun getHead(): Point = body.first()
    fun getBody(): List<Point> = body
    fun serialize(): String {
        val snapshot = body.toList() // COPIE
        return snapshot.joinToString("|") { "${it.x},${it.y}" }
    }

    fun setBody(newBody: List<Point>) {
        body.clear()
        body.addAll(newBody)
    }

}