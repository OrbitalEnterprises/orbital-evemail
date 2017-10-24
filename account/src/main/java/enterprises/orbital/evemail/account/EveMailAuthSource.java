package enterprises.orbital.evemail.account;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import enterprises.orbital.base.OrbitalProperties;
import enterprises.orbital.oauth.UserAccount;
import enterprises.orbital.oauth.UserAuthSource;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

import javax.persistence.*;
import java.io.IOException;
import java.util.Date;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * User authentication sources. There may be multiple entries associated with a single UserAccount.
 */
@Entity
@Table(
    name = "evemail_auth_source",
    indexes = {
        @Index(
            name = "charIndex",
            columnList = "characterID"),
        @Index(
            name = "sourceAndScreenIndex",
            columnList = "source, screenName")
    })
@NamedQueries({
    @NamedQuery(
        name = "EveMailAuthSource.findByAcctAndSource",
        query = "SELECT c FROM EveMailAuthSource c where c.account = :account and c.source = :source"),
    @NamedQuery(
        name = "EveMailAuthSource.allSourcesByAcct",
        query = "SELECT c FROM EveMailAuthSource c where c.account = :account order by c.last desc"),
    @NamedQuery(
        name = "EveMailAuthSource.all",
        query = "SELECT c FROM EveMailAuthSource c"),
    @NamedQuery(
        name = "EveMailAuthSource.allBySourceAndScreenname",
        query = "SELECT c FROM EveMailAuthSource c where c.source = :source and c.screenName = :screenname"),
})
@ApiModel(
    description = "Authentication source for a user")
@JsonSerialize(
    typing = JsonSerialize.Typing.DYNAMIC)
public class EveMailAuthSource implements UserAuthSource {
  private static final Logger log = Logger.getLogger(EveMailAuthSource.class.getName());

  @Id
  @GeneratedValue(
      strategy = GenerationType.SEQUENCE,
      generator = "em_seq")
  @SequenceGenerator(
      name = "em_seq",
      initialValue = 100000,
      allocationSize = 10,
      sequenceName = "em_sequence")
  @ApiModelProperty(
      value = "Unique source ID")
  @JsonProperty("sid")
  protected long sid;

  @ManyToOne
  @JoinColumn(
      name = "characterID",
      referencedColumnName = "characterID")
  @JsonProperty("account")
  private EveMailAccount account;

  @ApiModelProperty(
      value = "Name of authentication source")
  @JsonProperty("source")
  private String source;

  @ApiModelProperty(
      value = "Screen name for this source")
  @JsonProperty("screenName")
  private String screenName;

  @ApiModelProperty(
      value = "Source specific authentication details")
  @JsonProperty("details")
  @Lob
  @Column(
      length = 102400)
  private String details;

  @ApiModelProperty(
      value = "Last time (milliseconds UTC) this source was used to authenticate")
  @JsonProperty("last")
  private long last = -1;

  public EveMailAccount getUserAccount() {
    return account;
  }

  @Override
  public EveMailAccount getOwner() {
    return account;
  }

  @Override
  public String getSource() {
    return source;
  }

  @Override
  public String getScreenName() {
    return screenName;
  }

  public void setScreenName(
      String screenName) {
    this.screenName = screenName;
  }

  public String getDetails() {
    return details;
  }

  public void setDetails(
      String details) {
    this.details = details;
  }

  public long getLast() {
    return last;
  }

  public void setLast(
      long last) {
    this.last = last;
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + ((account == null) ? 0 : account.hashCode());
    result = prime * result + ((details == null) ? 0 : details.hashCode());
    result = prime * result + (int) (last ^ (last >>> 32));
    result = prime * result + ((screenName == null) ? 0 : screenName.hashCode());
    result = prime * result + (int) (sid ^ (sid >>> 32));
    result = prime * result + ((source == null) ? 0 : source.hashCode());
    return result;
  }

  @Override
  public boolean equals(
      Object obj) {
    if (this == obj) return true;
    if (obj == null) return false;
    if (getClass() != obj.getClass()) return false;
    EveMailAuthSource other = (EveMailAuthSource) obj;
    if (account == null) {
      if (other.account != null) return false;
    } else if (!account.equals(other.account)) return false;
    if (details == null) {
      if (other.details != null) return false;
    } else if (!details.equals(other.details)) return false;
    if (last != other.last) return false;
    if (screenName == null) {
      if (other.screenName != null) return false;
    } else if (!screenName.equals(other.screenName)) return false;
    if (sid != other.sid) return false;
    if (source == null) {
      if (other.source != null) return false;
    } else if (!source.equals(other.source)) return false;
    return true;
  }

  @Override
  public String toString() {
    return "EveMailAuthSource [sid=" + sid + ", account=" + account + ", source=" + source + ", screenName=" + screenName + ", details=" + details
        + ", last=" + last + "]";
  }

  public static EveMailAuthSource getSource(
      final EveMailAccount acct,
      final String source) {
    try {
      return EveMailAccountProvider.getFactory()
                                   .runTransaction(() -> {
                                   TypedQuery<EveMailAuthSource> getter = EveMailAccountProvider.getFactory()
                                                                                                .getEntityManager()
                                                                                                .createNamedQuery("EveMailAuthSource.findByAcctAndSource", EveMailAuthSource.class);
                                   getter.setParameter("account", acct);
                                   getter.setParameter("source", source);
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

  public static void removeSource(
      final EveMailAccount acct,
      final String source) {
    try {
      EveMailAccountProvider.getFactory()
                            .runTransaction(() -> {
                              EveMailAuthSource existing = getSource(acct, source);
                              if (existing != null) {
                                EveMailAccountProvider.getFactory().getEntityManager().remove(existing);
                              }
                            });
    } catch (Exception e) {
      log.log(Level.SEVERE, "query error", e);
    }
  }

  public static List<EveMailAuthSource> getAllSources(
      final EveMailAccount acct) {
    try {
      return EveMailAccountProvider.getFactory()
                                   .runTransaction(() -> {
                                   TypedQuery<EveMailAuthSource> getter = EveMailAccountProvider.getFactory()
                                                                                                .getEntityManager()
                                                                                                .createNamedQuery("EveMailAuthSource.allSourcesByAcct", EveMailAuthSource.class);
                                   getter.setParameter("account", acct);
                                   return getter.getResultList();
                                 });
    } catch (Exception e) {
      log.log(Level.SEVERE, "query error", e);
    }
    return null;
  }

  public static EveMailAuthSource getLastUsedSource(
      final EveMailAccount acct) {
    try {
      return EveMailAccountProvider.getFactory()
                                   .runTransaction(() -> {
                                   TypedQuery<EveMailAuthSource> getter = EveMailAccountProvider.getFactory()
                                                                                                .getEntityManager()
                                                                                                .createNamedQuery("EveMailAuthSource.allSourcesByAcct", EveMailAuthSource.class);
                                   getter.setParameter("account", acct);
                                   getter.setMaxResults(1);
                                   List<EveMailAuthSource> results = getter.getResultList();
                                   return results.isEmpty() ? null : results.get(0);
                                 });
    } catch (Exception e) {
      log.log(Level.SEVERE, "query error", e);
    }
    return null;
  }

  public static List<EveMailAuthSource> getAll() throws IOException {
    try {
      return EveMailAccountProvider.getFactory()
                                   .runTransaction(() -> {
                                   TypedQuery<EveMailAuthSource> getter = EveMailAccountProvider.getFactory()
                                                                                                .getEntityManager()
                                                                                                .createNamedQuery("EveMailAuthSource.all",
                                                                                                                         EveMailAuthSource.class);
                                   return getter.getResultList();
                                 });
    } catch (Exception e) {
      log.log(Level.SEVERE, "query error", e);
    }
    return null;
  }

  public static EveMailAuthSource getBySourceScreenname(
      final String source,
      final String screenName) {
    try {
      return EveMailAccountProvider.getFactory()
                                   .runTransaction(() -> {
                                   TypedQuery<EveMailAuthSource> getter = EveMailAccountProvider.getFactory()
                                                                                                .getEntityManager()
                                                                                                .createNamedQuery("EveMailAuthSource.allBySourceAndScreenname", EveMailAuthSource.class);
                                   getter.setParameter("source", source);
                                   getter.setParameter("screenname", screenName);
                                   getter.setMaxResults(1);
                                   List<EveMailAuthSource> results = getter.getResultList();
                                   return results.isEmpty() ? null : results.get(0);
                                 });
    } catch (Exception e) {
      log.log(Level.SEVERE, "query error", e);
    }
    return null;
  }

  public static EveMailAuthSource createSource(
      final EveMailAccount owner,
      final String source,
      final String screenName,
      final String details) {
    try {
      return EveMailAccountProvider.getFactory()
                                   .runTransaction(() -> {
                                   EveMailAuthSource result = getSource(owner, source);
                                   if (result != null) return result;
                                   result = new EveMailAuthSource();
                                   result.account = owner;
                                   result.source = source;
                                   result.setScreenName(screenName);
                                   result.setDetails(details);
                                   result.setLast(OrbitalProperties.getCurrentTime());
                                   return EveMailAccountProvider.getFactory()
                                                                .getEntityManager()
                                                                .merge(result);
                                 });
    } catch (Exception e) {
      log.log(Level.SEVERE, "query error", e);
    }
    return null;
  }

  public static EveMailAuthSource touch(
      final EveMailAuthSource source) {
    try {
      return EveMailAccountProvider.getFactory()
                                   .runTransaction(() -> {
                                   EveMailAuthSource result = getSource(source.getUserAccount(), source.getSource());
                                   if (result == null)
                                     throw new IOException("Input source could not be found: " + source.toString());
                                   result.setLast(OrbitalProperties.getCurrentTime());
                                   return EveMailAccountProvider.getFactory()
                                                                .getEntityManager()
                                                                .merge(result);
                                 });
    } catch (Exception e) {
      log.log(Level.SEVERE, "query error", e);
    }
    return null;
  }

  @Override
  public String getBody() {
    return details;
  }

  @Override
  public void touch() {
    touch(this);
  }

  @Override
  public void updateAccount(
      UserAccount existing) {
    // NOP
  }

  @Override
  public Date getLastSignOn() {
    return new Date(last);
  }

}
