package com.linedex.desktop;

final class ContextTarget {
    final AppItem app;
    final TaskRepository.TaskEntry task;

    ContextTarget(final AppItem app, final TaskRepository.TaskEntry task) {
        this.app = app;
        this.task = task;
    }
}
