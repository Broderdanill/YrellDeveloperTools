package se.yrell.developertools.midtier;

import com.bmc.arsys.studio.model.store.IAuthenticationStore;
import com.bmc.arsys.studio.model.store.IStore;
import com.bmc.arsys.studio.model.store.StoreManager;

/** Read credentials only when required from an ALREADY CONNECTED BMC store.
 * Never persist or log the password; the AR ticket itself is not a MidTier Config Tool session.
 */
public final class MidTierStudioCredentials {
    private MidTierStudioCredentials() { }

    public static MidTierHttpClient.Credentials forServer(String arServer) {
        if (arServer == null || arServer.isBlank()) {
            throw new IllegalStateException("No AR Server is associated with this editor.");
        }
        for (IStore store : StoreManager.getInstance().getStores()) {
            if (!store.isConnected() || !arServer.equalsIgnoreCase(store.getName())) continue;
            if (!(store instanceof IAuthenticationStore authentication)) {
                throw new IllegalStateException("The connected AR Server does not expose authentication information.");
            }
            String user = authentication.getUser();
            String password = authentication.getPassword();
            if (user == null || user.isBlank() || password == null || password.isEmpty()) {
                throw new IllegalStateException("Developer Studio has no reusable username/password for this server (for example RSSO). Reconnect using AR credentials, or use Configuration Tool manually. No flush was sent.");
            }
            return new MidTierHttpClient.Credentials(user, password, authentication.getPort(), authentication.getAuthentication());
        }
        throw new IllegalStateException("No connected AR Server store matches '" + arServer + "'. No flush was sent.");
    }
}
