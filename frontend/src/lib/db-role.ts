export type DbRoleEnv = {
  CAMPX_DB_APP_ROLE?: string;
  CAMPX_DB_REQUIRE_APP_ROLE?: string;
  [key: string]: string | undefined;
};

const ROLE_NAME = /^[a-z_][a-z0-9_]{0,62}$/;

/**
 * pg connection `options` that sets the session role at startup.
 * PostgreSQL SET ROLE cannot take a bound parameter, so the name is
 * validated and then placed in libpq startup options.
 * Returns undefined when no application role is configured.
 */
export function resolveDbRoleOptions(env: DbRoleEnv): string | undefined {
  const role = env.CAMPX_DB_APP_ROLE;

  if (role === undefined || role.trim() === '') {
    if (env.CAMPX_DB_REQUIRE_APP_ROLE === 'true') {
      throw new Error(
        'CAMPX_DB_REQUIRE_APP_ROLE=true requires CAMPX_DB_APP_ROLE. Refusing to open a database connection without an application role.',
      );
    }
    return undefined;
  }

  if (!ROLE_NAME.test(role)) {
    throw new Error(
      'CAMPX_DB_APP_ROLE must match /^[a-z_][a-z0-9_]{0,62}$/ before it can be used as a connection startup role.',
    );
  }

  return `-c role=${role}`;
}
