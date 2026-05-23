package me.autobot.client;

import java.util.ArrayList;
import java.util.List;

public class DelayTask {

    public static final List<DelayTask> TASKS = new ArrayList<>();

    private final int delay;
    private final Runnable runnable;
    private int pass;

    public DelayTask(int delay, Runnable r) {
        this.delay = delay;
        this.runnable = r;
        pass = 0;
    }

    public boolean tryExecute() {
        if (pass == delay) {
            runnable.run();
            return true;
        }
        pass++;
        return false;
    }
}
