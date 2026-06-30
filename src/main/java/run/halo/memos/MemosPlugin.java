package run.halo.memos;

import run.halo.app.plugin.BasePlugin;
import run.halo.app.plugin.PluginContext;

/**
 * Plugin main class. Lifecycle hooks are intentionally empty: runtime work is
 * handled by Spring beans (proxy endpoints, Finder, and image-cache warmup).
 */
public class MemosPlugin extends BasePlugin {

    public MemosPlugin(PluginContext pluginContext) {
        super(pluginContext);
    }
}
