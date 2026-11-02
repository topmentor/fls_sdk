package com.sox.flssdk.model;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

public class Buffer<E> {
    private static final int CAPACITY = 100;

    public Queue<E> queue;
    public int capacity;

    public Buffer() {
        this(CAPACITY);
    }

    public Buffer(int capacity) {
        this.queue = new LinkedList<>();
        this.capacity = capacity;
    }

    public void enqueue(E item) {
        if (this.queue.size() >= this.capacity) {
            this.queue.remove();
        }

        this.queue.add(item);
    }


    public List<E> toList() {
        return new ArrayList<>(queue);
    }
}
