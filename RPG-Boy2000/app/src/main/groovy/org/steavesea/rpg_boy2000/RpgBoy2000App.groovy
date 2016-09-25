package org.steavesea.rpg_boy2000

import android.app.Application
import dagger.ObjectGraph

public class RpgBoy2000App extends Application {
    private ObjectGraph graph;

    @Override
    public void onCreate() {
        super.onCreate();

        graph = ObjectGraph.create(
                new AndroidModule(this),
                new AndyGenModule()
        );
    }

    public void inject(Object object) {
        graph.inject(object);
    }
}