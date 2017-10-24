package enterprises.orbital.evemail.account;

import enterprises.orbital.oauth.UserAccount;
import enterprises.orbital.oauth.UserAccountProvider;
import enterprises.orbital.oauth.UserAuthSource;

public class EveMailUserAccountProvider implements UserAccountProvider {

  @Override
  public UserAccount getAccount(String uid) {
    int user_id;
    try {
      user_id = Integer.valueOf(uid);
    } catch (NumberFormatException e) {
      user_id = 0;
    }
    return EveMailAccount.getAccountByID(user_id);
  }

  @Override
  public UserAuthSource getSource(UserAccount acct, String source) {
    assert acct instanceof EveMailAccount;
    return EveMailAuthSource.getSource((EveMailAccount) acct, source);
  }

  @Override
  public void removeSourceIfExists(UserAccount acct, String source) {
    // NOP
  }

  @Override
  public UserAuthSource getBySourceScreenname(String source, String screenName) {
    return EveMailAuthSource.getBySourceScreenname(source, screenName);
  }

  @Override
  public UserAuthSource createSource(UserAccount newUser, String source, String screenName, String body) {
    assert newUser instanceof EveMailAccount;
    return EveMailAuthSource.createSource((EveMailAccount) newUser, source, screenName, body);
  }

  @Override
  public UserAccount createNewUserAccount(boolean disabled) {
    // NOP - user accounts are created elsewhere
    return null;
  }

}
