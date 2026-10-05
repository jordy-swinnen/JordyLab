package be.jordylab.app;

import android.content.Intent;
import android.os.Bundle;
import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        deliverLaunchShare(savedInstanceState);
    }

    /**
     * Capacitor hands a share to plugins only through onNewIntent, i.e. when the app is already running. When the
     * share sheet starts the app from scratch the SEND intent is the launch intent and the share-target plugin never
     * sees it: the app opens and nothing happens (BUG-062). Replay the launch intent once so the plugin reports it; it
     * keeps the event until the web side listens.
     *
     * Not replayed on a recreate (savedInstanceState is set) or when the app comes back from the recents list, where
     * Android re-delivers the original intent and the same share would arrive twice.
     */
    private void deliverLaunchShare(Bundle savedInstanceState) {
        Intent launch = getIntent();
        if (savedInstanceState != null || launch == null || getBridge() == null) {
            return;
        }
        boolean fromRecents = (launch.getFlags() & Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY) != 0;
        boolean isShare = Intent.ACTION_SEND.equals(launch.getAction()) || Intent.ACTION_SEND_MULTIPLE.equals(launch.getAction());
        if (isShare && !fromRecents) {
            getBridge().onNewIntent(launch);
        }
    }
}
