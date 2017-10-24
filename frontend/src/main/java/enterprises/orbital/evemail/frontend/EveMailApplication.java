package enterprises.orbital.evemail.frontend;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

import javax.ws.rs.core.Application;

import enterprises.orbital.base.OrbitalProperties;
import enterprises.orbital.base.PersistentProperty;
import enterprises.orbital.db.DBPropertyProvider;
import enterprises.orbital.evemail.account.EveMailAccountProvider;
import enterprises.orbital.evemail.account.EveMailUserAccountProvider;
import enterprises.orbital.oauth.AuthUtil;

public class EveMailApplication extends Application {
  public static final String PROP_APP_PATH = "enterprises.orbital.evemail.apppath";
  public static final String DEF_APP_PATH = "http://localhost/evemail";

  public EveMailApplication() throws IOException {
    // Populate properties
    OrbitalProperties.addPropertyFile("EveMail.properties");
    // Sent persistence unit
    PersistentProperty.setProvider(new DBPropertyProvider(OrbitalProperties.getGlobalProperty(EveMailAccountProvider.EVEMAIL_PU_PROP)));
    // Set UserAccountProvider provider
    AuthUtil.setUserAccountProvider(new EveMailUserAccountProvider());
  }

  @Override
  public Set<Class<?>> getClasses() {
    Set<Class<?>> resources = new HashSet<Class<?>>();
    // Local resources
    resources.add(ReleaseWS.class);
    resources.add(AccountWS.class);
    // Swagger additions
    resources.add(io.swagger.jaxrs.listing.ApiListingResource.class);
    resources.add(io.swagger.jaxrs.listing.SwaggerSerializers.class);
    // Return resource set
    return resources;
  }

}
