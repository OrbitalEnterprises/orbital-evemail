package enterprises.orbital.evemail.account;

import enterprises.orbital.base.OrbitalProperties;
import enterprises.orbital.db.ConnectionFactory;

public class EveMailAccountProvider {
  public static final String EVEMAIL_PU_PROP = "enterprises.orbital.evemail.persistence_unit";
  public static final String EVEMAIL_PU_DEFAULT = "evemail-pu";
  public static final String PROP_EVE_AUTH_CLIENT_ID = "enterprises.orbital.auth.eve_client_id";
  public static final String PROP_EVE_AUTH_SECRET_KEY = "enterprises.orbital.auth.eve_secret_key";

  public static ConnectionFactory getFactory() {
    return ConnectionFactory.getFactory(OrbitalProperties.getGlobalProperty(EVEMAIL_PU_PROP,
                                                                            EVEMAIL_PU_DEFAULT));
  }

}
