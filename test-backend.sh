#!/bin/bash
# VakilConnect backend smoke test.
# Run from anywhere once the app is up: ./test-backend.sh
# Requires: curl, jq (brew install jq if missing)

BASE="http://localhost:8080"
PASS=0
FAIL=0

check() {
  local desc="$1" expected="$2" actual="$3"
  if [ "$actual" = "$expected" ]; then
    echo "  PASS  [$actual] $desc"
    PASS=$((PASS+1))
  else
    echo "  FAIL  [$actual, expected $expected] $desc"
    FAIL=$((FAIL+1))
  fi
}

echo "=== 0. Is the server even up? ==="
UP=$(curl -s -o /dev/null -w "%{http_code}" "$BASE/v3/api-docs")
check "GET /v3/api-docs (public, sanity check)" "200" "$UP"
if [ "$UP" != "200" ]; then
  echo ""
  echo "Server not reachable/healthy on $BASE. Start it with:"
  echo "  cd backend && ./mvnw spring-boot:run"
  exit 1
fi

echo ""
echo "=== 1. Auth: register + login (public endpoints) ==="
CLIENT_EMAIL="client_$(date +%s)@test.com"
LAWYER_EMAIL="lawyer_$(date +%s)@test.com"

REG_CODE=$(curl -s -o /tmp/reg_client.json -w "%{http_code}" -X POST "$BASE/api/auth/register" \
  -H "Content-Type: application/json" \
  -d "{\"fullName\":\"Test Client\",\"email\":\"$CLIENT_EMAIL\",\"password\":\"password123\",\"phoneNumber\":\"9876543210\",\"role\":\"CLIENT\"}")
check "POST /api/auth/register (client)" "201" "$REG_CODE"
cat /tmp/reg_client.json; echo ""

REG_LAWYER_CODE=$(curl -s -o /tmp/reg_lawyer.json -w "%{http_code}" -X POST "$BASE/api/auth/register" \
  -H "Content-Type: application/json" \
  -d "{\"fullName\":\"Test Lawyer\",\"email\":\"$LAWYER_EMAIL\",\"password\":\"password123\",\"phoneNumber\":\"9876543211\",\"role\":\"LAWYER\"}")
check "POST /api/auth/register (lawyer)" "201" "$REG_LAWYER_CODE"

LOGIN_CODE=$(curl -s -o /tmp/login_client.json -w "%{http_code}" -X POST "$BASE/api/auth/login" \
  -H "Content-Type: application/json" \
  -d "{\"email\":\"$CLIENT_EMAIL\",\"password\":\"password123\"}")
check "POST /api/auth/login (client)" "200" "$LOGIN_CODE"
CLIENT_TOKEN=$(jq -r '.token' /tmp/login_client.json 2>/dev/null)
echo "  client token: ${CLIENT_TOKEN:0:20}..."

LOGIN_LAWYER_CODE=$(curl -s -o /tmp/login_lawyer.json -w "%{http_code}" -X POST "$BASE/api/auth/login" \
  -H "Content-Type: application/json" \
  -d "{\"email\":\"$LAWYER_EMAIL\",\"password\":\"password123\"}")
check "POST /api/auth/login (lawyer)" "200" "$LOGIN_LAWYER_CODE"
LAWYER_TOKEN=$(jq -r '.token' /tmp/login_lawyer.json 2>/dev/null)

echo ""
echo "=== 2. /api/users/me (protected, needs valid token) ==="
ME_CODE=$(curl -s -o /tmp/me.json -w "%{http_code}" "$BASE/api/users/me" -H "Authorization: Bearer $CLIENT_TOKEN")
check "GET /api/users/me with client token" "200" "$ME_CODE"

NO_TOKEN_CODE=$(curl -s -o /dev/null -w "%{http_code}" "$BASE/api/users/me")
check "GET /api/users/me with NO token (should be 401/403, not 200)" "401" "$NO_TOKEN_CODE" || true

echo ""
echo "=== 3. Lawyer creates profile, admin verifies, public search finds it ==="
PROFILE_CODE=$(curl -s -o /tmp/profile.json -w "%{http_code}" -X POST "$BASE/api/lawyer/profile" \
  -H "Authorization: Bearer $LAWYER_TOKEN" -H "Content-Type: application/json" \
  -d '{"barCouncilNumber":"BC12345","experienceYears":5,"bio":"Experienced lawyer","consultationFee":1500,"city":"Mumbai","officeAddress":"123 Court Rd","specializations":["Family Law","Civil Law"]}')
check "POST /api/lawyer/profile (create lawyer profile)" "201" "$PROFILE_CODE"
cat /tmp/profile.json; echo ""
LAWYER_ID=$(jq -r '.id' /tmp/profile.json 2>/dev/null)

echo ""
echo "=== 4. Public lawyer search (no auth needed) ==="
SEARCH_CODE=$(curl -s -o /tmp/search.json -w "%{http_code}" "$BASE/api/lawyers?page=0&size=10")
check "GET /api/lawyers (public, no token)" "200" "$SEARCH_CODE"

echo ""
echo "=== 5. Cross-role access control ==="
WRONG_ROLE_CODE=$(curl -s -o /dev/null -w "%{http_code}" "$BASE/api/lawyer/dashboard" -H "Authorization: Bearer $CLIENT_TOKEN")
check "GET /api/lawyer/dashboard with CLIENT token (should be 403)" "403" "$WRONG_ROLE_CODE"

RIGHT_ROLE_CODE=$(curl -s -o /dev/null -w "%{http_code}" "$BASE/api/lawyer/dashboard" -H "Authorization: Bearer $LAWYER_TOKEN")
check "GET /api/lawyer/dashboard with LAWYER token (should be 200)" "200" "$RIGHT_ROLE_CODE"

echo ""
echo "=== 6. Appointment booking (client books, lawyer must be verified first) ==="
echo "  NOTE: lawyer must be verified by an admin before booking works."
echo "  Skipping unless you export ADMIN_EMAIL/ADMIN_PASSWORD for a seeded admin user."
if [ -n "$ADMIN_EMAIL" ] && [ -n "$ADMIN_PASSWORD" ]; then
  ADMIN_LOGIN_CODE=$(curl -s -o /tmp/login_admin.json -w "%{http_code}" -X POST "$BASE/api/auth/login" \
    -H "Content-Type: application/json" \
    -d "{\"email\":\"$ADMIN_EMAIL\",\"password\":\"$ADMIN_PASSWORD\"}")
  check "POST /api/auth/login (admin)" "200" "$ADMIN_LOGIN_CODE"
  ADMIN_TOKEN=$(jq -r '.token' /tmp/login_admin.json 2>/dev/null)

  VERIFY_CODE=$(curl -s -o /dev/null -w "%{http_code}" -X PUT "$BASE/api/admin/lawyers/$LAWYER_ID/verify" \
    -H "Authorization: Bearer $ADMIN_TOKEN")
  check "PUT /api/admin/lawyers/{id}/verify" "200" "$VERIFY_CODE"

  BOOK_CODE=$(curl -s -o /tmp/book.json -w "%{http_code}" -X POST "$BASE/api/client/appointments" \
    -H "Authorization: Bearer $CLIENT_TOKEN" -H "Content-Type: application/json" \
    -d "{\"lawyerId\":\"$LAWYER_ID\",\"appointmentDate\":\"2026-08-01\",\"appointmentTime\":\"10:00:00\",\"consultationMode\":\"ONLINE\",\"notes\":\"Test booking\"}")
  check "POST /api/client/appointments (book)" "201" "$BOOK_CODE"

  HISTORY_CODE=$(curl -s -o /dev/null -w "%{http_code}" "$BASE/api/client/appointments" -H "Authorization: Bearer $CLIENT_TOKEN")
  check "GET /api/client/appointments (history)" "200" "$HISTORY_CODE"

  ANALYTICS_CODE=$(curl -s -o /dev/null -w "%{http_code}" "$BASE/api/admin/analytics" -H "Authorization: Bearer $ADMIN_TOKEN")
  check "GET /api/admin/analytics" "200" "$ANALYTICS_CODE"
else
  echo "  (set ADMIN_EMAIL and ADMIN_PASSWORD env vars to also test booking/admin flows)"
fi

echo ""
echo "================================"
echo "RESULTS: $PASS passed, $FAIL failed"
echo "================================"
