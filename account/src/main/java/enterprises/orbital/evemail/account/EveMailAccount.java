package enterprises.orbital.evemail.account;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.github.scribejava.core.model.OAuth2AccessToken;
import enterprises.orbital.base.OrbitalProperties;
import enterprises.orbital.base.PersistentPropertyKey;
import enterprises.orbital.oauth.EVEAuthHandler;
import enterprises.orbital.oauth.UserAccount;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

import javax.persistence.*;
import java.io.IOException;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Objects of this class represent EveMail e-mail accounts.  Each account maintains an ESI token which
 * provides the mail scopes needed to send/receive e-mail with that character.
 */
@Entity
@Table(
    name = "evemail_account",
    indexes = {
        @Index(
            name = "charIndex",
            columnList = "characterID",
            unique = true),
    })
@NamedQueries({
    @NamedQuery(
        name = "EveMailAccount.findByID",
        query = "SELECT c FROM EveMailAccount c where c.characterID = :charid")
})
@ApiModel(
    description = "EveMail Account")
@JsonIgnoreProperties({
    "accessToken", "refreshToken", "accountPassword"
})
public class EveMailAccount implements UserAccount, PersistentPropertyKey<String> {
  protected static final Logger log = Logger.getLogger(EveMailAccount.class.getName());

  // The character ID associated with this key.  This was the character used during OAuth authentication.
  // We also use character ID as the unique ID for the account, limiting each character to at most
  // one account.
  @Id
  @ApiModelProperty(
      value = "Unique character ID.")
  @JsonProperty("characterID")
  private int characterID;

  // Admin indicator
  @ApiModelProperty(
      value = "True if user is an admin, false otherwise")
  @JsonProperty("admin")
  private boolean admin;

  // The character name associated with this key. This was the character used during OAuth authentication.
  @ApiModelProperty(
      value = "Name of character for this account.")
  @JsonProperty("characterName")
  private String characterName;

  // The corporation name associated with this key.
  @ApiModelProperty(
      value = "Name of corporation for this account.")
  @JsonProperty("corporationName")
  private String corporationName;

  // The corporation ID associated with this key.
  @ApiModelProperty(value = "Unique corporation ID.")
  @JsonProperty("corporationID")
  private int corporationID;

  // Account password for mail authentication purposes
  private String accountPassword;

  @ApiModelProperty(
      value = "Date (milliseconds UTC) when account was created")
  @JsonProperty("createDate")
  private long createDate = -1;

  // Date this account was last accessed from a mail client
  @ApiModelProperty(value = "Last date (milliseconds UTC) when this account was accessed.")
  @JsonProperty("lastAccess")
  private long lastAccess = -1;

  // Latest access token
  private String accessToken;

  // Expiry date (millis UTC) of access token
  @ApiModelProperty(value = "Date (milliseconds UTC) when access token will expire.")
  @JsonProperty("accessTokenExpiry")
  private long accessTokenExpiry;

  // Latest refresh token
  private String refreshToken;

  // True if refresh token is non-null and non-empty, false otherwise.
  // Set before returning token data to web client.
  @Transient
  @JsonProperty("valid")
  private boolean valid;

  public void setAccountPassword(String accountPassword) {
    this.accountPassword = accountPassword;
  }

  public void setLastAccess(long lastAccess) {
    this.lastAccess = lastAccess;
  }

  public void setAccessToken(String accessToken) {
    this.accessToken = accessToken;
  }

  public void setAccessTokenExpiry(long accessTokenExpiry) {
    this.accessTokenExpiry = accessTokenExpiry;
  }

  public void setRefreshToken(String refreshToken) {
    this.refreshToken = refreshToken;
  }

  public void setValid(boolean valid) { this.valid = valid; }

  public void setAdmin(boolean admin) { this.admin = admin; }

  public String getCharacterName() {
    return characterName;
  }

  public int getCharacterID() {
    return characterID;
  }

  public String getCorporationName() {
    return corporationName;
  }

  public int getCorporationID() {
    return corporationID;
  }

  public String getAccountName() {
    return String.valueOf(characterID) + "@char.evemail.orbital.enterprises";
  }

  public String getAccountPassword() {
    return accountPassword;
  }

  public long getCreateDate() {
    return createDate;
  }

  public long getLastAccess() {
    return lastAccess;
  }

  public String getAccessToken() { return accessToken; }

  public long getAccessTokenExpiry() {
    return accessTokenExpiry;
  }

  public String getRefreshToken() {
    return refreshToken;
  }

  public boolean isValid() { return valid; }

  public void updateValid() {
    valid = refreshToken != null && !refreshToken.isEmpty();
  }

  public boolean isAdmin() {
    return admin;
  }

  @Override
  public String getPeristentPropertyKey(String field) {
    // Key scheme: EveMailAccount.<UID>.<field>
    return "EveMailAccount." + String.valueOf(characterID) + "." + field;
  }

  @Override
  public boolean isDisabled() {
    return false;
  }

  @Override
  public String getUid() {
    return String.valueOf(characterID);
  }

  @Override
  public void touch() {
    EveMailAccount.touch(this);
  }

  @Override
  public Date getJoinTime() {
    return new Date(createDate);
  }

  @Override
  public Date getLastSignOn() {
    return new Date(lastAccess);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    EveMailAccount that = (EveMailAccount) o;

    if (characterID != that.characterID) return false;
    if (corporationID != that.corporationID) return false;
    if (createDate != that.createDate) return false;
    if (!characterName.equals(that.characterName)) return false;
    return corporationName.equals(that.corporationName);
  }

  @Override
  public int hashCode() {
    int result = characterID;
    result = 31 * result + characterName.hashCode();
    result = 31 * result + corporationName.hashCode();
    result = 31 * result + corporationID;
    result = 31 * result + (int) (createDate ^ (createDate >>> 32));
    return result;
  }

  @Override
  public String toString() {
    return "EveMailAccount{" +
        "admin=" + admin +
        ", characterName='" + characterName + '\'' +
        ", characterID=" + characterID +
        ", corporationName='" + corporationName + '\'' +
        ", corporationID=" + corporationID +
        ", accountPassword='" + accountPassword + '\'' +
        ", createDate=" + createDate +
        ", lastAccess=" + lastAccess +
        ", accessToken='" + accessToken + '\'' +
        ", accessTokenExpiry=" + accessTokenExpiry +
        ", refreshToken='" + refreshToken + '\'' +
        ", valid=" + valid +
        '}';
  }

  public static EveMailAccount createAccount(final String characterName,
                                             final int characterID,
                                             final String corporationName,
                                             final int corporationID,
                                             final long createDate) {
    EveMailAccount newAccount = null;
    try {
      newAccount = EveMailAccountProvider.getFactory()
                                         .runTransaction(() -> {
                                           EveMailAccount result = new EveMailAccount();
                                           result.characterName = characterName;
                                           result.characterID = characterID;
                                           result.corporationName = corporationName;
                                           result.corporationID = corporationID;
                                           result.createDate = createDate;
                                           return EveMailAccountProvider.getFactory()
                                                                        .getEntityManager()
                                                                        .merge(result);
                                         });
    } catch (Exception e) {
      log.log(Level.SEVERE, "query error", e);
    }
    return newAccount;
  }

  public static EveMailAccount getAccountByID(final int charid) {
    try {
      return EveMailAccountProvider.getFactory()
                                   .runTransaction(() -> {
                                     TypedQuery<EveMailAccount> getter = EveMailAccountProvider.getFactory()
                                                                                               .getEntityManager()
                                                                                               .createNamedQuery("EveMailAccount.findByID", EveMailAccount.class);
                                     getter.setParameter("charid", charid);
                                     try {
                                       return getter.getSingleResult();
                                     } catch (NoResultException e) {
                                       return null;
                                     }
                                   });
    } catch (Exception e) {
      log.log(Level.SEVERE, "query error", e);
    }
    return null;
  }

  public static boolean deleteAccount(final int charid) {
    try {
      EveMailAccountProvider.getFactory()
                            .runTransaction(() -> {
                              EveMailAccount account = getAccountByID(charid);
                              if (account != null) {
                                for (EveMailAuthSource source : EveMailAuthSource.getAllSources(account))
                                  EveMailAccountProvider.getFactory().getEntityManager().remove(source);
                                EveMailAccountProvider.getFactory()
                                                      .getEntityManager()
                                                      .remove(account);
                                // TODO: delete James e-mail account
                                // TODO: this should also log us out if successful
                              }
                            });
      return true;
    } catch (Exception e) {
      log.log(Level.SEVERE, "query error", e);
    }
    return false;
  }

  public static EveMailAccount update(final EveMailAccount account) {
    try {
      return EveMailAccountProvider.getFactory()
                                   .runTransaction(() -> EveMailAccountProvider.getFactory()
                                                                               .getEntityManager()
                                                                               .merge(account));
    } catch (Exception e) {
      log.log(Level.SEVERE, "query error", e);
      return null;
    }
  }

  public static EveMailAccount touch(EveMailAccount account) {
    account.setLastAccess(OrbitalProperties.getCurrentTime());
    return update(account);
  }

  public static String refresh(int charid, long expiryWindow)
      throws IOException {
    // Find token
    EveMailAccount account = EveMailAccount.getAccountByID(charid);
    if (account == null) throw new IOException("No account with ID: " + charid);
    // Ensure the access token is valid, if not attempt to renew it
    if (account.getAccessTokenExpiry() - OrbitalProperties.getCurrentTime() < expiryWindow) {
      // Key within expiry window, refresh
      String refreshToken = account.getRefreshToken();
      if (refreshToken == null) throw new IOException("No valid refresh token for key: " + charid);
      String eveClientID = OrbitalProperties.getGlobalProperty(EveMailAccountProvider.PROP_EVE_TOKEN_CLIENT_ID);
      String eveSecretKey = OrbitalProperties.getGlobalProperty(EveMailAccountProvider.PROP_EVE_TOKEN_SECRET_KEY);
      OAuth2AccessToken newToken = EVEAuthHandler.doRefresh(eveClientID, eveSecretKey, refreshToken);
      if (newToken == null) {
        // Invalidate refresh token
        account.setRefreshToken(null);
        EveMailAccount.update(account);
        throw new IOException("Failed to refresh token for key: " + charid);
      }
      account.setAccessToken(newToken.getAccessToken());
      account.setAccessTokenExpiry(OrbitalProperties.getCurrentTime() +
                                       TimeUnit.MILLISECONDS.convert(newToken.getExpiresIn(), TimeUnit.SECONDS));
      account.setRefreshToken(newToken.getRefreshToken());
      account = EveMailAccount.update(account);
      if (account == null) throw new IOException("Failed to save refreshed token for key: " + charid);
    }
    return account.getAccessToken();
  }

}
