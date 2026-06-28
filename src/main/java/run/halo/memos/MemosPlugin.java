package run.halo.memos;

import run.halo.app.plugin.BasePlugin;
import run.halo.app.plugin.PluginContext;

/**
 * Plugin main class. Lifecycle hooks are intentionally empty: this plugin is a
 * stateless reverse proxy to a self-hosted memos instance plus a Console view,
 * so it registers no extensions and owns no background work.
 */
public class MemosPlugin extends BasePlugin {

    public MemosPlugin(PluginContext pluginContext) {
        super(pluginContext);
    }
}
