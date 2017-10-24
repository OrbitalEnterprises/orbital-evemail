package enterprises.orbital.evemail.frontend;

import com.github.scribejava.core.builder.ServiceBuilder;
import com.github.scribejava.core.model.OAuth2AccessToken;
import com.github.scribejava.core.model.OAuthRequest;
import com.github.scribejava.core.model.Verb;
import com.github.scribejava.core.oauth.OAuth20Service;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import enterprises.orbital.base.OrbitalProperties;
import enterprises.orbital.base.Stamper;
import enterprises.orbital.eve.esi.client.api.CharacterApi;
import enterprises.orbital.eve.esi.client.api.CorporationApi;
import enterprises.orbital.eve.esi.client.invoker.ApiException;
import enterprises.orbital.eve.esi.client.model.GetCharactersCharacterIdOk;
import enterprises.orbital.eve.esi.client.model.GetCorporationsCorporationIdOk;
import enterprises.orbital.evemail.account.EveMailAccount;
import enterprises.orbital.evemail.account.EveMailAccountProvider;
import enterprises.orbital.evemail.account.EveMailAuthSource;
import enterprises.orbital.oauth.*;
import io.swagger.annotations.*;
import org.apache.http.client.utils.URIBuilder;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

@Path("/ws/v1/account")
@Consumes({
    "application/json"
})
@Produces({
    "application/json"
})
@Api(
    tags = {
        "Account"
    },
    produces = "application/json",
    consumes = "application/json")
public class AccountWS {
  private static final Logger log = Logger.getLogger(AccountWS.class.getName());

  public static final String PROP_EVE_AUTH_DEBUG_MODE = "enterprises.orbital.auth.eve_debug_mode";
  public static final String PROP_EVE_AUTH_DEBUG_USER = "enterprises.orbital.auth.eve_debug_user";
  public static final String PROP_EVE_AUTH_CLIENT_ID = "enterprises.orbital.auth.eve_client_id";
  public static final String PROP_EVE_AUTH_SECRET_KEY = "enterprises.orbital.auth.eve_secret_key";

  // The EVE SSO verify URL is generic so we can use whatever authentication is using.
  public static final String PROP_EVE_VERIFY_URL = "enterprises.orbital.auth.eve_verify_url";

  protected static URIBuilder makeStandardBuilder(
      HttpServletRequest req)
      throws MalformedURLException, URISyntaxException {
    URIBuilder builder = new URIBuilder(OrbitalProperties.getGlobalProperty(EveMailApplication.PROP_APP_PATH, EveMailApplication.DEF_APP_PATH) + "/");
    return builder;
  }

  protected static String makeErrorCallback(
      HttpServletRequest req,
      String msg)
      throws MalformedURLException, URISyntaxException {
    URIBuilder builder = makeStandardBuilder(req);
    builder.addParameter("auth_error", msg);
    return builder.toString();
  }

  protected static void loginDebugUser(
      String source,
      String screenName,
      HttpServletRequest req)
      throws IOException {
    // Verify the debug user doesn't exist yet
    int charID = 1801683792;
    EveMailAccount existing = EveMailAccount.getAccountByID(charID);

    if (existing == null) {
      // Debug user doesn't exist, create it
      String charName = "reygar burnt";
      String corpName = "Dark Planet Ventures";
      int corpID = 98007623;
      existing = EveMailAccount.createAccount(charName, charID, corpName, corpID, OrbitalProperties.getCurrentTime());
      Random gen = new Random(OrbitalProperties.getCurrentTime());
      String initialPassword = Stamper.fastDigest(String.valueOf(gen.nextLong()));
      existing.setAccountPassword(initialPassword);
      existing.setAccessToken("12345");
      existing.setAccessTokenExpiry(OrbitalProperties.getCurrentTime() + TimeUnit.MILLISECONDS.convert(30, TimeUnit.DAYS));
      existing.setRefreshToken("ABCDE");
      EveMailAccount.update(existing);
      AuthUtil.createSource(existing, "eve", "reygar burnt", "debug user");
    }
    UserAuthSource authSource = AuthUtil.getBySourceScreenname("eve", "reygar burnt");
    AuthUtil.signOn(req, authSource.getOwner(), authSource);
  }

  /**
   * Start OAuth flow.  On success, /callback entry point is called.
   *
   * @param request incoming request
   * @return redirect response
   * @throws IOException if an error occurs starting the flow
   * @throws URISyntaxException if the callback URL is malformed
   */
  protected Response startFlow(HttpServletRequest request)
      throws IOException, URISyntaxException {

    // Mail scope list
    final String scopes = "esi-mail.organize_mail.v1 esi-mail.read_mail.v1 esi-mail.send_mail.v1";

    // Start OAuth flow
    URIBuilder builder = makeStandardBuilder(request);
    String eveClientID = OrbitalProperties.getGlobalProperty(PROP_EVE_AUTH_CLIENT_ID);
    String eveSecretKey = OrbitalProperties.getGlobalProperty(PROP_EVE_AUTH_SECRET_KEY);
    builder.setPath(builder.getPath() + "api/ws/v1/account/callback");
    String redirect = EVEAuthHandler.doGet(eveClientID, eveSecretKey, builder.toString(), scopes, null, request);
    if (redirect == null) redirect = makeErrorCallback(request, "Failed to initiate OAuth authentication flow.  Please try again.");
    log.fine("Redirecting to: " + redirect);
    return Response.temporaryRedirect(new URI(redirect)).build();
  }


  @Path("/login")
  @GET
  @ApiOperation(
      value = "Authenticate and authorize EVE mail scopes.")
  @ApiResponses(
      value = {
          @ApiResponse(
              code = 307,
              message = "Temporary redirect to an OAuth endpoint to initiate authentication."),
          @ApiResponse(
              code = 500,
              message = "Internal service error.",
              response = ServiceError.class),
      })
  public Response login(
      @Context HttpServletRequest request) throws IOException, URISyntaxException {
    if (OrbitalProperties.getBooleanGlobalProperty(PROP_EVE_AUTH_DEBUG_MODE, false)) {
      // In this case, skip the usual login scheme and immediately log in the user with a debug user.
      // This mode is normally only enabled for local test since EVE OAuth login doesn't work in that case.
      String eveDebugUser = OrbitalProperties.getGlobalProperty(PROP_EVE_AUTH_DEBUG_USER, "eveuser");
      loginDebugUser("eve", eveDebugUser, request);
      return Response.temporaryRedirect(new URI(makeStandardBuilder(request).toString())).build();
    }

    // Start new flow
    return startFlow(request);
  }

  @Path("/reauth")
  @GET
  @ApiOperation(
      value = "Re-authorize the currently logged in user by first logging out, then loggin in.")
  @ApiResponses(
      value = {
          @ApiResponse(
              code = 307,
              message = "Temporary redirect to an OAuth endpoint to initiate authentication."),
          @ApiResponse(
              code = 500,
              message = "Internal service error.",
              response = ServiceError.class),
      })
  public Response reauth(
      @Context HttpServletRequest request) throws IOException, URISyntaxException {
    // Logout any current user
    logout(request);

    // Start new OAuth flow
    return startFlow(request);
  }

  @Path("/callback")
  @GET
  @ApiOperation(
      value = "Handle OAuth callback for authorization.")
  @ApiResponses(
      value = {
          @ApiResponse(
              code = 307,
              message = "Temporary redirect back to main site."),
          @ApiResponse(
              code = 400,
              message = "Unable to complete authentication.")
      })
  public Response callback(
      @Context HttpServletRequest req)
      throws IOException, URISyntaxException {

    String redirect = makeStandardBuilder(req).toString();

    // Load auth properties
    String eveClientID = OrbitalProperties.getGlobalProperty(PROP_EVE_AUTH_CLIENT_ID);
    String eveSecretKey = OrbitalProperties.getGlobalProperty(PROP_EVE_AUTH_SECRET_KEY);
    String eveVerifyURL = OrbitalProperties.getGlobalProperty(PROP_EVE_VERIFY_URL);

    // Construct the service to use for verification.
    OAuth20Service service = new ServiceBuilder().apiKey(eveClientID).apiSecret(eveSecretKey).build(EVEApi.instance());

    try {
      // Exchange for access token
      OAuth2AccessToken accessToken = service.getAccessToken(req.getParameter("code"));

      // Retrieve character for selected login
      OAuthRequest request = new OAuthRequest(Verb.GET, eveVerifyURL, service.getConfig());
      service.signRequest(accessToken, request);
      com.github.scribejava.core.model.Response response = request.send();
      if (!response.isSuccessful()) throw new IOException("credential request was not successful!");
      JsonObject responseObject = (new JsonParser()).parse(response.getBody()).getAsJsonObject();
      String charName = responseObject.get("CharacterName").getAsString();
      int charID = responseObject.get("CharacterID").getAsInt();

      // If we already have an account with this character, then we must be resetting the refresh token.
      // Otherwise, this is a new account.
      EveMailAccount existing = EveMailAccount.getAccountByID(charID);
      if (existing == null) {
        // Proceed with creating a new account
        String corpName;
        int corpID;
        try {
          CharacterApi charApi = new CharacterApi();
          GetCharactersCharacterIdOk charResult = charApi.getCharactersCharacterId(charID, "tranquility",
                                                                                   null, null);
          corpID = charResult.getCorporationId();
          CorporationApi corpApi = new CorporationApi();
          GetCorporationsCorporationIdOk result = corpApi.getCorporationsCorporationId(charResult.getCorporationId(),
                                                                                       "tranquility",
                                                                                       null, null);
          corpName = result.getCorporationName();
        } catch (ApiException e) {
          log.log(Level.SEVERE, "Exception while retrieving corporation information", e);
          redirect = makeErrorCallback(req, "Unable to retrieve needed ESI information during authentication.  Please try again.");
          return Response.temporaryRedirect(new URI(redirect))
                         .build();
        }

        existing = EveMailAccount.createAccount(charName, charID, corpName, corpID, OrbitalProperties.getCurrentTime());
        if (existing == null) {
          log.log(Level.SEVERE, "Failed to create properly authenticated account");
          redirect = makeErrorCallback(req, "Failed to create new account.  Please try again.");
          return Response.temporaryRedirect(new URI(redirect))
                         .build();
        }
        if (AuthUtil.createSource(existing, "eve", charName, response.getBody()) == null) {
          log.log(Level.SEVERE, "Failed to create source for just created account: " + existing);
          EveMailAccount.deleteAccount(existing.getCharacterID());
          redirect = makeErrorCallback(req, "Failed to create new account.  Please try again.");
          return Response.temporaryRedirect(new URI(redirect))
                         .build();
        }

        // Generate and set a new random password
        Random gen = new Random(OrbitalProperties.getCurrentTime());
        String initialPassword = Stamper.fastDigest(String.valueOf(gen.nextLong()));
        initialPassword = initialPassword.substring(0, 10);
        existing.setAccountPassword(initialPassword);
        if (EveMailAccount.update(existing) == null) {
          log.log(Level.SEVERE, "Failed to set initial password for new account: " + existing);
          EveMailAccount.deleteAccount(existing.getCharacterID());
          redirect = makeErrorCallback(req, "Failed to create new account.  Please try again.");
          return Response.temporaryRedirect(new URI(redirect))
                         .build();
        }
      }

      // Update token properties for this account
      existing.setAccessToken(accessToken.getAccessToken());
      existing.setAccessTokenExpiry(OrbitalProperties.getCurrentTime() +
                                        TimeUnit.MILLISECONDS.convert(accessToken.getExpiresIn(), TimeUnit.SECONDS));
      existing.setRefreshToken(accessToken.getRefreshToken());
      EveMailAccount.update(existing);

      // Mark the user as signed in
      EveMailAuthSource source = EveMailAuthSource.getSource(existing, "eve");
      if (source == null) {
        log.log(Level.SEVERE, "Failed to find source for existing user: " + existing);
        redirect = makeErrorCallback(req, "Failed to complete authentication.  Please try again.");
        return Response.temporaryRedirect(new URI(redirect))
                       .build();
      }
      AuthUtil.signOn(req, existing, source);

    } catch (Exception e) {
      log.log(Level.WARNING, "Failed EVE authentication with error: ", e);
      redirect = makeErrorCallback(req,"Failed to complete authentication.  Please try again.");
    }

    log.fine("Redirecting to: " + redirect);
    return Response.temporaryRedirect(new URI(redirect)).build();
  }

  @Path("/logout")
  @GET
  @ApiOperation(
      value = "Logout the current logged in user.")
  @ApiResponses(
      value = {
          @ApiResponse(
              code = 307,
              message = "Temporary redirect back to EveMail site.")
      })
  public Response logout(
      @Context HttpServletRequest req)
      throws IOException, URISyntaxException {
    URIBuilder builder = makeStandardBuilder(req);
    String redirect = LogoutHandler.doGet(null, builder.toString(), req);
    // This should never happen for the normal logout case.
    assert redirect != null;
    log.fine("Redirecting to: " + redirect);
    return Response.temporaryRedirect(new URI(redirect)).build();
  }


  @Path("/delete")
  @GET
  @ApiOperation(
      value = "Delete current logged in user.")
  @ApiResponses(
      value = {
          @ApiResponse(
              code = 307,
              message = "Temporary redirect back to EveMail site."),
          @ApiResponse(
              code = 500,
              message = "Internal service error",
              response = ServiceError.class),
      })
  public Response delete(
      @Context HttpServletRequest request) throws IOException, URISyntaxException {
    // Retrieve user and verify as needed
    EveMailAccount user = (EveMailAccount) AuthUtil.getCurrentUser(request);
    if (user == null) {
      // NOP - just redirect back to main site
      return Response.temporaryRedirect(new URI(makeStandardBuilder(request).toString())).build();
    }
    // Delete and return
    if (!EveMailAccount.deleteAccount(user.getCharacterID())) {
      ServiceError errMsg = new ServiceError(
          Status.INTERNAL_SERVER_ERROR.getStatusCode(), "Internal error deleting account, contact admin if this problem persists");
      return Response.status(Status.INTERNAL_SERVER_ERROR).entity(errMsg).build();
    }
    return logout(request);
  }

  @Path("/user")
  @GET
  @ApiOperation(
      value = "Get information about the current logged in user",
      notes = "User information about the current logged in user, or null if no user logged in")
  @ApiResponses(
      value = {
          @ApiResponse(
              code = 200,
              message = "logged in user, or null",
              response = EveMailAccount.class),
      })
  public Response getUser(
      @Context HttpServletRequest request) {
    // Retrieve current logged in user
    EveMailAccount user = (EveMailAccount) AuthUtil.getCurrentUser(request);
    if (user != null) user.updateValid();
    return Response.ok().entity(user).build();
  }

  @Path("/user_password")
  @GET
  @ApiOperation(
      value = "Get logged in users password",
      notes = "Current users password, or ")
  @ApiResponses(
      value = {
          @ApiResponse(
              code = 200,
              message = "logged in user, or null",
              response = String.class),
          @ApiResponse(
              code = 500,
              message = "Internal service error",
              response = ServiceError.class),
      })
  public Response getUserPassword(
      @Context HttpServletRequest request) {
    // Retrieve current logged in user
    EveMailAccount user = (EveMailAccount) AuthUtil.getCurrentUser(request);
    if (user != null) {
      return Response.ok().entity(new Object() {
        @SuppressWarnings("unused")
        public final String password = user.getAccountPassword();
      }).build();
    } else {
      ServiceError errMsg = new ServiceError(
          Status.FORBIDDEN.getStatusCode(), "Not logged in, reload the page to login.");
      return Response.status(Status.FORBIDDEN).entity(errMsg).build();
    }
  }

  @Path("/change_password")
  @POST
  @ApiOperation(
      value = "Change logged in user's password")
  @ApiResponses(
      value = {
          @ApiResponse(
              code = 200,
              message = "password changed successfully"),
          @ApiResponse(
              code = 400,
              message = "Bad password length.  Password must be 4-12 characters in length.",
              response = ServiceError.class),
          @ApiResponse(
              code = 403,
              message = "Not logged in",
              response = ServiceError.class),
          @ApiResponse(
              code = 500,
              message = "Internal service error",
              response = ServiceError.class),
      })
  public Response changeUserPassword(
      @Context HttpServletRequest request,
      @ApiParam(
          name = "password",
          required = true,
          value = "New user password") String password) {
    // Retrieve current logged in user
    EveMailAccount user = (EveMailAccount) AuthUtil.getCurrentUser(request);
    if (user == null) {
      // Not logged in, error
      ServiceError errMsg = new ServiceError(
          Status.FORBIDDEN.getStatusCode(), "Not logged in, reload the page to login.");
      return Response.status(Status.FORBIDDEN).entity(errMsg).build();
    }
    // Sanity check password length
    if (password.length() < 4 || password.length() > 12) {
      ServiceError errMsg = new ServiceError(
          Status.BAD_REQUEST.getStatusCode(), "Password must be between 4 and 12 characters in length.");
      return Response.status(Status.BAD_REQUEST).entity(errMsg).build();
    }
    // Install new password and return
    user.setAccountPassword(password);
    if (EveMailAccount.update(user) == null) {
      // Update failed
      ServiceError errMsg = new ServiceError(
          Status.INTERNAL_SERVER_ERROR.getStatusCode(), "Failed to update password.  Please try again.");
      return Response.status(Status.INTERNAL_SERVER_ERROR).entity(errMsg).build();
    }
    return Response.ok().build();
  }

  @Path("/isadmin")
  @GET
  @ApiOperation(
      value = "Check whether the current user is an admin",
      notes = "Returns true if the current user is logged in and admin, false otherwise")
  @ApiResponses(
      value = {
          @ApiResponse(
              code = 200,
              message = "admin status of logged in user",
              response = Boolean.class),
          @ApiResponse(
              code = 401,
              message = "requesting user not authenticated",
              response = ServiceError.class),
          @ApiResponse(
              code = 500,
              message = "Internal account service service error",
              response = ServiceError.class),
      })
  public Response checkAdmin(
      @Context HttpServletRequest request) {
    // Retrieve current logged in user
    final EveMailAccount user = (EveMailAccount) AuthUtil.getCurrentUser(request);
    if (user == null) {
      ServiceError errMsg = new ServiceError(Status.UNAUTHORIZED.getStatusCode(), "User not logged in");
      return Response.status(Status.UNAUTHORIZED).entity(errMsg).build();
    }
    // Retrieve and return unfinished
    return Response.ok().entity(new Object() {
      @SuppressWarnings("unused")
      public final boolean isAdmin = user.isAdmin();
    }).build();
  }


}
