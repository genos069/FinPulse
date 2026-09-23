import test from "node:test";
import assert from "node:assert/strict";

process.env.EXPO_PUBLIC_AUTH_API_URL = "https://finpulse.test";
const authModule = import("../src/services/auth");
test("login uses the existing backend contract and converts expiry seconds to milliseconds", async (t) => {
  const { signIn } = await authModule;
  let sent: { url?: string; body?: string } = {};
  t.mock.method(
    globalThis,
    "fetch",
    async (url: string, options: RequestInit) => {
      sent = { url, body: String(options.body) };
      return new Response(
        JSON.stringify({
          accessToken: "test-token",
          expiresIn: 3600,
          user: {
            id: "test-user",
            name: "Test User",
            email: "test@example.com",
          },
        }),
      );
    },
  );
  const start = Date.now(),
    session = await signIn(" Test@Example.com ", "example-password");
  assert.equal(sent.url, "https://finpulse.test/api/auth/login");
  assert.equal(JSON.parse(sent.body!).email, "test@example.com");
  assert.ok(session.expiresAt >= start + 3600000);
  assert.equal(session.user.id, "test-user");
  assert.equal("password" in session, false);
});
test("registration accepts the UserResponse without pretending it is a login token", async (t) => {
  const { register } = await authModule;
  t.mock.method(globalThis, "fetch", async (url: string) => {
    assert.equal(url, "https://finpulse.test/api/auth/register");
    return new Response(
      JSON.stringify({
        id: "test-user",
        name: "Test User",
        email: "test@example.com",
      }),
      { status: 201 },
    );
  });
  assert.equal(
    await register("Test User", "test@example.com", "example-password"),
    undefined,
  );
});
test("failed credentials and malformed responses cannot create a session", async (t) => {
  const { signIn } = await authModule;
  const mock = t.mock.method(
    globalThis,
    "fetch",
    async () => new Response("{}", { status: 401 }),
  );
  await assert.rejects(() => signIn("test@example.com", "wrong"), /incorrect/);
  mock.mock.mockImplementation(async () => new Response("{}"));
  await assert.rejects(
    () => signIn("test@example.com", "example-password"),
    /unexpected response/,
  );
});
