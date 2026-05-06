#!/usr/bin/env bash
# =============================================================================
# scripts/data_loader.sh
#
# Populates the club-management database with demo data via the GraphQL API.
#
# USAGE:
#   ./scripts/data_loader.sh              # load data only
#   ./scripts/data_loader.sh --verify     # load data + run verification queries
#
# PREREQUISITES:
#   - jq   (apt install jq  /  brew install jq)
#   - curl
#   - The application must be running (./gradlew bootRun, dev profile).
#     The H2 in-memory database resets on every app restart; restart the app
#     before re-running this script to avoid duplicate-email errors.
#
# Override the target URL:
#   BASE_URL=http://localhost:9090/graphql ./scripts/data_loader.sh
# =============================================================================

set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080/graphql}"
VERIFY="${1:-}"

# ---- guards -----------------------------------------------------------------

command -v jq   >/dev/null 2>&1 || { echo "ERROR: jq is required (apt install jq / brew install jq)"; exit 1; }
command -v curl >/dev/null 2>&1 || { echo "ERROR: curl is required"; exit 1; }

# ---- helpers ----------------------------------------------------------------

# Send a GraphQL query/mutation; print raw JSON response.
gql() {
  local query="$1"
  local payload
  payload=$(jq -n --arg q "$query" '{"query": $q}')
  curl -s -X POST "$BASE_URL" \
    -H "Content-Type: application/json" \
    -d "$payload"
}

# Send a GraphQL query/mutation; extract a field via jq path; abort on errors.
# Usage: gql_field ".data.createTrainer.id" "$QUERY"
gql_field() {
  local jqpath="$1"
  local query="$2"
  local response
  response=$(gql "$query")
  if echo "$response" | jq -e '.errors | length > 0' >/dev/null 2>&1; then
    echo "ERROR: GraphQL returned errors (path: ${jqpath}):" >&2
    echo "$response" | jq '.errors' >&2
    exit 1
  fi
  echo "$response" | jq -r "$jqpath"
}

# ============================================================
# T2 — Create trainers
# ============================================================
echo "==> T2: Creating trainers"

TRAINER_ANA=$(gql_field ".data.createTrainer.id" 'mutation {
  createTrainer(input: {
    firstName: "Ana"
    lastName: "Koller"
    email: "ana.koller@example.com"
    phoneNumber: "+43 699 1001"
    hourlyRate: 35.00
    paymentMode: "MONTHLY"
    autoApproveHours: true
  }) { id }
}')
echo "  Trainer Ana:   $TRAINER_ANA"

TRAINER_BEN=$(gql_field ".data.createTrainer.id" 'mutation {
  createTrainer(input: {
    firstName: "Ben"
    lastName: "Hartmann"
    email: "ben.hartmann@example.com"
    phoneNumber: "+43 699 1002"
    hourlyRate: 40.00
    paymentMode: "PER_SESSION"
    autoApproveHours: false
  }) { id }
}')
echo "  Trainer Ben:   $TRAINER_BEN"

TRAINER_CLARA=$(gql_field ".data.createTrainer.id" 'mutation {
  createTrainer(input: {
    firstName: "Clara"
    lastName: "Steiner"
    email: "clara.steiner@example.com"
    phoneNumber: "+43 699 1003"
    hourlyRate: 30.00
    paymentMode: "MONTHLY"
    autoApproveHours: true
  }) { id }
}')
echo "  Trainer Clara: $TRAINER_CLARA"

# ============================================================
# T3 — Create membership types (DRAFT → ACTIVE)
# ============================================================
echo "==> T3: Creating membership types"

MT_MONTHLY=$(gql_field ".data.createMembershipType.id" 'mutation {
  createMembershipType(input: {
    name: "Monthly Basic"
    description: "Month-to-month access with grace period"
    price: 49.00
    duration: 1
    unit: "MONTHS"
    proratedMode: true
    gracePeriodDays: 7
  }) { id }
}')
echo "  MembershipType Monthly Basic:      $MT_MONTHLY"
gql_field ".data.changeMembershipTypeStatus.id" \
  "mutation { changeMembershipTypeStatus(id: \"$MT_MONTHLY\", status: \"ACTIVE\") { id } }" \
  >/dev/null

MT_QUARTERLY=$(gql_field ".data.createMembershipType.id" 'mutation {
  createMembershipType(input: {
    name: "Quarterly Standard"
    description: "Three-month block, best value for regulars"
    price: 129.00
    duration: 3
    unit: "MONTHS"
    proratedMode: false
    gracePeriodDays: 14
  }) { id }
}')
echo "  MembershipType Quarterly Standard: $MT_QUARTERLY"
gql_field ".data.changeMembershipTypeStatus.id" \
  "mutation { changeMembershipTypeStatus(id: \"$MT_QUARTERLY\", status: \"ACTIVE\") { id } }" \
  >/dev/null

MT_ANNUAL=$(gql_field ".data.createMembershipType.id" 'mutation {
  createMembershipType(input: {
    name: "Annual Premium"
    description: "Full year access, all sessions included"
    price: 449.00
    duration: 12
    unit: "MONTHS"
    proratedMode: false
    gracePeriodDays: 30
  }) { id }
}')
echo "  MembershipType Annual Premium:     $MT_ANNUAL"
gql_field ".data.changeMembershipTypeStatus.id" \
  "mutation { changeMembershipTypeStatus(id: \"$MT_ANNUAL\", status: \"ACTIVE\") { id } }" \
  >/dev/null

MT_CLASS=$(gql_field ".data.createMembershipType.id" 'mutation {
  createMembershipType(input: {
    name: "10-Class Pass"
    description: "Ten individual class credits, no expiry"
    price: 89.00
    duration: 10
    unit: "WEEKS"
    proratedMode: false
    gracePeriodDays: 5
  }) { id }
}')
echo "  MembershipType 10-Class Pass:      $MT_CLASS"
gql_field ".data.changeMembershipTypeStatus.id" \
  "mutation { changeMembershipTypeStatus(id: \"$MT_CLASS\", status: \"ACTIVE\") { id } }" \
  >/dev/null

# ============================================================
# T4 — Create sessions
# ============================================================
echo "==> T4: Creating sessions"

SESSION_STRENGTH=$(gql_field ".data.createSession.id" "mutation {
  createSession(input: {
    name: \"Monday Strength\"
    sessionType: \"TRAINING\"
    dayOfWeek: \"MONDAY\"
    startTime: \"07:00\"
    endTime: \"08:00\"
    location: \"Gym Floor A\"
    trainerId: \"$TRAINER_ANA\"
  }) { id }
}")
echo "  Session Monday Strength: $SESSION_STRENGTH"

SESSION_YOGA=$(gql_field ".data.createSession.id" "mutation {
  createSession(input: {
    name: \"Wed Yoga Flow\"
    sessionType: \"TRAINING\"
    dayOfWeek: \"WEDNESDAY\"
    startTime: \"18:00\"
    endTime: \"19:00\"
    location: \"Studio B\"
    trainerId: \"$TRAINER_CLARA\"
  }) { id }
}")
echo "  Session Wed Yoga Flow:   $SESSION_YOGA"

SESSION_CROSSFIT=$(gql_field ".data.createSession.id" "mutation {
  createSession(input: {
    name: \"Fri Crossfit\"
    sessionType: \"TRAINING\"
    dayOfWeek: \"FRIDAY\"
    startTime: \"06:30\"
    endTime: \"07:30\"
    location: \"Gym Floor A\"
    trainerId: \"$TRAINER_BEN\"
  }) { id }
}")
echo "  Session Fri Crossfit:    $SESSION_CROSSFIT"

SESSION_FREE=$(gql_field ".data.createSession.id" 'mutation {
  createSession(input: {
    name: "Sat Free Play"
    sessionType: "FREE_GAME"
    dayOfWeek: "SATURDAY"
    startTime: "10:00"
    endTime: "12:00"
    location: "Court 1"
  }) { id }
}')
echo "  Session Sat Free Play:   $SESSION_FREE"

SESSION_PILATES=$(gql_field ".data.createSession.id" "mutation {
  createSession(input: {
    name: \"Thu Pilates\"
    sessionType: \"TRAINING\"
    dayOfWeek: \"THURSDAY\"
    startTime: \"19:00\"
    endTime: \"20:00\"
    location: \"Studio B\"
    trainerId: \"$TRAINER_CLARA\"
  }) { id }
}")
echo "  Session Thu Pilates:     $SESSION_PILATES"

# ============================================================
# T5 — Assign sessions to membership types
# ============================================================
echo "==> T5: Assigning sessions to membership types"

# Monthly Basic → Monday Strength, Sat Free Play
gql_field ".data.assignSessionToMembership.id" \
  "mutation { assignSessionToMembership(membershipTypeId: \"$MT_MONTHLY\", sessionId: \"$SESSION_STRENGTH\") { id } }" \
  >/dev/null
gql_field ".data.assignSessionToMembership.id" \
  "mutation { assignSessionToMembership(membershipTypeId: \"$MT_MONTHLY\", sessionId: \"$SESSION_FREE\") { id } }" \
  >/dev/null
echo "  Monthly Basic: Monday Strength, Sat Free Play"

# Quarterly Standard → Monday Strength, Wed Yoga Flow, Fri Crossfit, Sat Free Play
gql_field ".data.assignSessionToMembership.id" \
  "mutation { assignSessionToMembership(membershipTypeId: \"$MT_QUARTERLY\", sessionId: \"$SESSION_STRENGTH\") { id } }" \
  >/dev/null
gql_field ".data.assignSessionToMembership.id" \
  "mutation { assignSessionToMembership(membershipTypeId: \"$MT_QUARTERLY\", sessionId: \"$SESSION_YOGA\") { id } }" \
  >/dev/null
gql_field ".data.assignSessionToMembership.id" \
  "mutation { assignSessionToMembership(membershipTypeId: \"$MT_QUARTERLY\", sessionId: \"$SESSION_CROSSFIT\") { id } }" \
  >/dev/null
gql_field ".data.assignSessionToMembership.id" \
  "mutation { assignSessionToMembership(membershipTypeId: \"$MT_QUARTERLY\", sessionId: \"$SESSION_FREE\") { id } }" \
  >/dev/null
echo "  Quarterly Standard: Monday Strength, Wed Yoga Flow, Fri Crossfit, Sat Free Play"

# Annual Premium → all 5 sessions
gql_field ".data.assignSessionToMembership.id" \
  "mutation { assignSessionToMembership(membershipTypeId: \"$MT_ANNUAL\", sessionId: \"$SESSION_STRENGTH\") { id } }" \
  >/dev/null
gql_field ".data.assignSessionToMembership.id" \
  "mutation { assignSessionToMembership(membershipTypeId: \"$MT_ANNUAL\", sessionId: \"$SESSION_YOGA\") { id } }" \
  >/dev/null
gql_field ".data.assignSessionToMembership.id" \
  "mutation { assignSessionToMembership(membershipTypeId: \"$MT_ANNUAL\", sessionId: \"$SESSION_CROSSFIT\") { id } }" \
  >/dev/null
gql_field ".data.assignSessionToMembership.id" \
  "mutation { assignSessionToMembership(membershipTypeId: \"$MT_ANNUAL\", sessionId: \"$SESSION_FREE\") { id } }" \
  >/dev/null
gql_field ".data.assignSessionToMembership.id" \
  "mutation { assignSessionToMembership(membershipTypeId: \"$MT_ANNUAL\", sessionId: \"$SESSION_PILATES\") { id } }" \
  >/dev/null
echo "  Annual Premium: all 5 sessions"

# 10-Class Pass → Fri Crossfit
gql_field ".data.assignSessionToMembership.id" \
  "mutation { assignSessionToMembership(membershipTypeId: \"$MT_CLASS\", sessionId: \"$SESSION_CROSSFIT\") { id } }" \
  >/dev/null
echo "  10-Class Pass: Fri Crossfit"

# ============================================================
# T6 — Create session occurrences (2026-05-06 to 2026-07-01)
# ============================================================
echo "==> T6: Creating session occurrences"

# Monday Strength (MONDAY): May 11, 18, 25; Jun 1, 8, 15, 22, 29
RESP_STRENGTH=$(gql "mutation {
  createSessionOccurrences(input: {
    sessionId: \"$SESSION_STRENGTH\"
    startDate: \"2026-05-06\"
    endDate: \"2026-07-01\"
  }) { id date }
}")
OCC_STRENGTH_1=$(echo "$RESP_STRENGTH" | jq -r '.data.createSessionOccurrences[0].id')
OCC_STRENGTH_2=$(echo "$RESP_STRENGTH" | jq -r '.data.createSessionOccurrences[1].id')
OCC_STRENGTH_3=$(echo "$RESP_STRENGTH" | jq -r '.data.createSessionOccurrences[2].id')
OCC_STRENGTH_CANCEL=$(echo "$RESP_STRENGTH" | jq -r '.data.createSessionOccurrences[3].id')
echo "  Monday Strength: $(echo "$RESP_STRENGTH" | jq '.data.createSessionOccurrences | length') occurrences"

# Wed Yoga Flow (WEDNESDAY): May 6, 13, 20, 27; Jun 3, 10, 17, 24
RESP_YOGA=$(gql "mutation {
  createSessionOccurrences(input: {
    sessionId: \"$SESSION_YOGA\"
    startDate: \"2026-05-06\"
    endDate: \"2026-07-01\"
  }) { id date }
}")
OCC_YOGA_CANCEL=$(echo "$RESP_YOGA" | jq -r '.data.createSessionOccurrences[3].id')
echo "  Wed Yoga Flow:   $(echo "$RESP_YOGA" | jq '.data.createSessionOccurrences | length') occurrences"

# Fri Crossfit (FRIDAY): May 8, 15, 22, 29; Jun 5, 12, 19, 26
RESP_CROSSFIT=$(gql "mutation {
  createSessionOccurrences(input: {
    sessionId: \"$SESSION_CROSSFIT\"
    startDate: \"2026-05-06\"
    endDate: \"2026-07-01\"
  }) { id date }
}")
OCC_CROSSFIT_1=$(echo "$RESP_CROSSFIT" | jq -r '.data.createSessionOccurrences[0].id')
OCC_CROSSFIT_2=$(echo "$RESP_CROSSFIT" | jq -r '.data.createSessionOccurrences[1].id')
OCC_CROSSFIT_CANCEL=$(echo "$RESP_CROSSFIT" | jq -r '.data.createSessionOccurrences[3].id')
echo "  Fri Crossfit:    $(echo "$RESP_CROSSFIT" | jq '.data.createSessionOccurrences | length') occurrences"

# Sat Free Play (SATURDAY): May 9, 16, 23, 30; Jun 6, 13, 20, 27
RESP_FREE=$(gql "mutation {
  createSessionOccurrences(input: {
    sessionId: \"$SESSION_FREE\"
    startDate: \"2026-05-06\"
    endDate: \"2026-07-01\"
  }) { id date }
}")
OCC_FREE_CANCEL=$(echo "$RESP_FREE" | jq -r '.data.createSessionOccurrences[3].id')
echo "  Sat Free Play:   $(echo "$RESP_FREE" | jq '.data.createSessionOccurrences | length') occurrences"

# Thu Pilates (THURSDAY): May 7, 14, 21, 28; Jun 4, 11, 18, 25
RESP_PILATES=$(gql "mutation {
  createSessionOccurrences(input: {
    sessionId: \"$SESSION_PILATES\"
    startDate: \"2026-05-06\"
    endDate: \"2026-07-01\"
  }) { id date }
}")
OCC_PILATES_1=$(echo "$RESP_PILATES" | jq -r '.data.createSessionOccurrences[0].id')
OCC_PILATES_2=$(echo "$RESP_PILATES" | jq -r '.data.createSessionOccurrences[1].id')
OCC_PILATES_3=$(echo "$RESP_PILATES" | jq -r '.data.createSessionOccurrences[2].id')
OCC_PILATES_CANCEL=$(echo "$RESP_PILATES" | jq -r '.data.createSessionOccurrences[3].id')
echo "  Thu Pilates:     $(echo "$RESP_PILATES" | jq '.data.createSessionOccurrences | length') occurrences"

echo "==> T6b: Completing occurrences for trainer hour logging"
# Ana: 3 Monday Strength occurrences
gql_field ".data.completeSessionOccurrence.id" \
  "mutation { completeSessionOccurrence(id: \"$OCC_STRENGTH_1\") { id } }" >/dev/null
gql_field ".data.completeSessionOccurrence.id" \
  "mutation { completeSessionOccurrence(id: \"$OCC_STRENGTH_2\") { id } }" >/dev/null
gql_field ".data.completeSessionOccurrence.id" \
  "mutation { completeSessionOccurrence(id: \"$OCC_STRENGTH_3\") { id } }" >/dev/null
# Ben: 2 Fri Crossfit occurrences
gql_field ".data.completeSessionOccurrence.id" \
  "mutation { completeSessionOccurrence(id: \"$OCC_CROSSFIT_1\") { id } }" >/dev/null
gql_field ".data.completeSessionOccurrence.id" \
  "mutation { completeSessionOccurrence(id: \"$OCC_CROSSFIT_2\") { id } }" >/dev/null
# Clara: 3 Thu Pilates occurrences
gql_field ".data.completeSessionOccurrence.id" \
  "mutation { completeSessionOccurrence(id: \"$OCC_PILATES_1\") { id } }" >/dev/null
gql_field ".data.completeSessionOccurrence.id" \
  "mutation { completeSessionOccurrence(id: \"$OCC_PILATES_2\") { id } }" >/dev/null
gql_field ".data.completeSessionOccurrence.id" \
  "mutation { completeSessionOccurrence(id: \"$OCC_PILATES_3\") { id } }" >/dev/null
echo "  Completed 8 occurrences (Ana×3, Ben×2, Clara×3)"

echo "==> T6c: Cancelling one occurrence per session"
gql_field ".data.cancelSessionOccurrence.id" \
  "mutation { cancelSessionOccurrence(id: \"$OCC_STRENGTH_CANCEL\") { id } }" >/dev/null
gql_field ".data.cancelSessionOccurrence.id" \
  "mutation { cancelSessionOccurrence(id: \"$OCC_YOGA_CANCEL\") { id } }" >/dev/null
gql_field ".data.cancelSessionOccurrence.id" \
  "mutation { cancelSessionOccurrence(id: \"$OCC_CROSSFIT_CANCEL\") { id } }" >/dev/null
gql_field ".data.cancelSessionOccurrence.id" \
  "mutation { cancelSessionOccurrence(id: \"$OCC_FREE_CANCEL\") { id } }" >/dev/null
gql_field ".data.cancelSessionOccurrence.id" \
  "mutation { cancelSessionOccurrence(id: \"$OCC_PILATES_CANCEL\") { id } }" >/dev/null
echo "  Cancelled 5 occurrences (one per session)"

# ============================================================
# T7 — Create members
# ============================================================
echo "==> T7: Creating members"

MEMBER_MARIA=$(gql_field ".data.createMember.id" 'mutation {
  createMember(input: {
    firstName: "Maria"
    lastName: "Gruber"
    email: "maria.gruber@example.com"
    phoneNumber: "+43 676 2001"
    memberSince: "2024-01-15"
  }) { id }
}')
echo "  Member Maria:   $MEMBER_MARIA"

MEMBER_THOMAS=$(gql_field ".data.createMember.id" 'mutation {
  createMember(input: {
    firstName: "Thomas"
    lastName: "Bauer"
    email: "thomas.bauer@example.com"
    phoneNumber: "+43 676 2002"
    memberSince: "2024-03-01"
  }) { id }
}')
echo "  Member Thomas:  $MEMBER_THOMAS"

MEMBER_SOPHIE=$(gql_field ".data.createMember.id" 'mutation {
  createMember(input: {
    firstName: "Sophie"
    lastName: "Wagner"
    email: "sophie.wagner@example.com"
    phoneNumber: "+43 676 2003"
    memberSince: "2023-09-10"
  }) { id }
}')
echo "  Member Sophie:  $MEMBER_SOPHIE"

MEMBER_LUKAS=$(gql_field ".data.createMember.id" 'mutation {
  createMember(input: {
    firstName: "Lukas"
    lastName: "Mayer"
    email: "lukas.mayer@example.com"
    phoneNumber: "+43 676 2004"
    memberSince: "2024-06-20"
  }) { id }
}')
echo "  Member Lukas:   $MEMBER_LUKAS"

MEMBER_ANNA=$(gql_field ".data.createMember.id" 'mutation {
  createMember(input: {
    firstName: "Anna"
    lastName: "Huber"
    email: "anna.huber@example.com"
    phoneNumber: "+43 676 2005"
    memberSince: "2025-01-05"
  }) { id }
}')
echo "  Member Anna:    $MEMBER_ANNA"

MEMBER_FELIX=$(gql_field ".data.createMember.id" 'mutation {
  createMember(input: {
    firstName: "Felix"
    lastName: "Schmidt"
    email: "felix.schmidt@example.com"
    phoneNumber: "+43 676 2006"
    memberSince: "2025-11-01"
  }) { id }
}')
echo "  Member Felix:   $MEMBER_FELIX"

# Transition Lukas → INACTIVE, Felix → FROZEN
gql_field ".data.changeMemberStatus.id" \
  "mutation { changeMemberStatus(input: { memberId: \"$MEMBER_LUKAS\", status: \"INACTIVE\", reason: \"Long-term absence\" }) { id } }" \
  >/dev/null
echo "  Lukas -> INACTIVE"
gql_field ".data.changeMemberStatus.id" \
  "mutation { changeMemberStatus(input: { memberId: \"$MEMBER_FELIX\", status: \"INACTIVE\", reason: \"Payment hold\" }) { id } }" \
  >/dev/null
echo "  Felix -> INACTIVE"

# ============================================================
# T8 — Subscribe members
# ============================================================
echo "==> T8: Subscribing members"

SUB_MARIA=$(gql_field ".data.subscribeMember.id" "mutation {
  subscribeMember(input: {
    memberId: \"$MEMBER_MARIA\"
    membershipTypeId: \"$MT_ANNUAL\"
    startDate: \"2026-01-01\"
    agreedPrice: 449.00
  }) { id }
}")
echo "  Sub Maria (Annual Premium):      $SUB_MARIA"

SUB_THOMAS=$(gql_field ".data.subscribeMember.id" "mutation {
  subscribeMember(input: {
    memberId: \"$MEMBER_THOMAS\"
    membershipTypeId: \"$MT_QUARTERLY\"
    startDate: \"2026-04-01\"
    agreedPrice: 129.00
  }) { id }
}")
echo "  Sub Thomas (Quarterly Standard): $SUB_THOMAS"

SUB_SOPHIE=$(gql_field ".data.subscribeMember.id" "mutation {
  subscribeMember(input: {
    memberId: \"$MEMBER_SOPHIE\"
    membershipTypeId: \"$MT_MONTHLY\"
    startDate: \"2026-05-01\"
    agreedPrice: 49.00
  }) { id }
}")
echo "  Sub Sophie (Monthly Basic):      $SUB_SOPHIE"

SUB_ANNA=$(gql_field ".data.subscribeMember.id" "mutation {
  subscribeMember(input: {
    memberId: \"$MEMBER_ANNA\"
    membershipTypeId: \"$MT_CLASS\"
    startDate: \"2026-04-15\"
    agreedPrice: 89.00
  }) { id }
}")
echo "  Sub Anna (10-Class Pass):        $SUB_ANNA"

# Felix start date 2026-04-10 → endDate 2026-05-10 (active/future);
# grace period (7 days) expires 2026-04-17, well past today (2026-05-05) → overdue.
SUB_FELIX=$(gql_field ".data.subscribeMember.id" "mutation {
  subscribeMember(input: {
    memberId: \"$MEMBER_FELIX\"
    membershipTypeId: \"$MT_MONTHLY\"
    startDate: \"2026-04-10\"
    agreedPrice: 49.00
  }) { id }
}")
echo "  Sub Felix (Monthly Basic):       $SUB_FELIX"

# ============================================================
# T9 — Record payments
# ============================================================
echo "==> T9: Recording payments"

gql_field ".data.recordPayment.id" \
  "mutation { recordPayment(input: { memberSubscriptionId: \"$SUB_MARIA\", amount: 449.00, currency: \"EUR\", paymentDate: \"2026-01-02\", notes: \"Full annual fee\" }) { id } }" \
  >/dev/null
echo "  Maria:  449.00 EUR (full annual fee)"

gql_field ".data.recordPayment.id" \
  "mutation { recordPayment(input: { memberSubscriptionId: \"$SUB_THOMAS\", amount: 129.00, currency: \"EUR\", paymentDate: \"2026-04-02\", notes: \"Q2 payment\" }) { id } }" \
  >/dev/null
echo "  Thomas: 129.00 EUR (Q2 payment)"

gql_field ".data.recordPayment.id" \
  "mutation { recordPayment(input: { memberSubscriptionId: \"$SUB_SOPHIE\", amount: 49.00, currency: \"EUR\", paymentDate: \"2026-05-02\", notes: \"May payment\" }) { id } }" \
  >/dev/null
echo "  Sophie:  49.00 EUR (May payment)"

gql_field ".data.recordPayment.id" \
  "mutation { recordPayment(input: { memberSubscriptionId: \"$SUB_ANNA\", amount: 50.00, currency: \"EUR\", paymentDate: \"2026-04-16\", notes: \"Partial payment\" }) { id } }" \
  >/dev/null
echo "  Anna:    50.00 EUR (partial)"
echo "  Felix:   no payment (intentional — exercises outstandingPayments + overdueSubscriptions)"

# ============================================================
# T10 — Upload and review payment document for Sophie
# ============================================================
echo "==> T10: Uploading and reviewing payment document for Sophie"

gql_field ".data.uploadPaymentDocument.id" "mutation {
  uploadPaymentDocument(input: {
    memberSubscriptionId: \"$SUB_SOPHIE\"
    fileName: \"may-receipt.pdf\"
    fileContent: \"JVBERi0xLjQK\"
    notes: \"Bank transfer screenshot\"
  }) { id }
}" >/dev/null
echo "  Uploaded may-receipt.pdf for Sophie"

gql_field ".data.reviewPaymentDocument.id" "mutation {
  reviewPaymentDocument(input: {
    memberSubscriptionId: \"$SUB_SOPHIE\"
    approved: true
    reason: \"Payment confirmed\"
  }) { id paymentStatus }
}" >/dev/null
echo "  Reviewed: Sophie subscription -> REVIEWED"

# ============================================================
# T11 — Log trainer hours
# ============================================================
echo "==> T11: Logging trainer hours"

# Ana: 3 approved entries via logTrainerHours (admin path → APPROVED)
gql_field ".data.logTrainerHours.id" \
  "mutation { logTrainerHours(input: { trainerId: \"$TRAINER_ANA\", sessionOccurrenceId: \"$OCC_STRENGTH_1\", hoursWorked: 1.0, notes: \"Monday Strength wk1\" }) { id } }" \
  >/dev/null
gql_field ".data.logTrainerHours.id" \
  "mutation { logTrainerHours(input: { trainerId: \"$TRAINER_ANA\", sessionOccurrenceId: \"$OCC_STRENGTH_2\", hoursWorked: 1.0, notes: \"Monday Strength wk2\" }) { id } }" \
  >/dev/null
gql_field ".data.logTrainerHours.id" \
  "mutation { logTrainerHours(input: { trainerId: \"$TRAINER_ANA\", sessionOccurrenceId: \"$OCC_STRENGTH_3\", hoursWorked: 1.0, notes: \"Monday Strength wk3\" }) { id } }" \
  >/dev/null
echo "  Ana: 3 approved log entries"

# Ben: 2 entries via submitTrainerHours (autoApproveHours=false → PENDING)
BEN_LOG_1=$(gql_field ".data.submitTrainerHours.id" \
  "mutation { submitTrainerHours(input: { trainerId: \"$TRAINER_BEN\", sessionOccurrenceId: \"$OCC_CROSSFIT_1\", hoursWorked: 1.0, notes: \"Fri Crossfit wk1\" }) { id } }")
BEN_LOG_2=$(gql_field ".data.submitTrainerHours.id" \
  "mutation { submitTrainerHours(input: { trainerId: \"$TRAINER_BEN\", sessionOccurrenceId: \"$OCC_CROSSFIT_2\", hoursWorked: 1.0, notes: \"Fri Crossfit wk2\" }) { id } }")
echo "  Ben: 2 pending entries ($BEN_LOG_1, $BEN_LOG_2)"

# Approve log 1, reject log 2
gql_field ".data.approveTrainerLog.id" \
  "mutation { approveTrainerLog(id: \"$BEN_LOG_1\") { id status } }" >/dev/null
echo "  Ben log 1 -> APPROVED"
gql_field ".data.rejectTrainerLog.id" \
  "mutation { rejectTrainerLog(input: { id: \"$BEN_LOG_2\", reason: \"Hours mismatch — session ran 45 min, not 60\" }) { id status } }" \
  >/dev/null
echo "  Ben log 2 -> REJECTED"

# Resubmit corrected hours (exercises full approval workflow)
gql_field ".data.resubmitTrainerLog.id" \
  "mutation { resubmitTrainerLog(input: { id: \"$BEN_LOG_2\", hoursWorked: 0.75, notes: \"Corrected: 45 min session\" }) { id status } }" \
  >/dev/null
echo "  Ben log 2 resubmitted (0.75 h) -> PENDING"

# Clara: 3 approved entries via logTrainerHours (admin path → APPROVED)
gql_field ".data.logTrainerHours.id" \
  "mutation { logTrainerHours(input: { trainerId: \"$TRAINER_CLARA\", sessionOccurrenceId: \"$OCC_PILATES_1\", hoursWorked: 1.0, notes: \"Thu Pilates wk1\" }) { id } }" \
  >/dev/null
gql_field ".data.logTrainerHours.id" \
  "mutation { logTrainerHours(input: { trainerId: \"$TRAINER_CLARA\", sessionOccurrenceId: \"$OCC_PILATES_2\", hoursWorked: 1.0, notes: \"Thu Pilates wk2\" }) { id } }" \
  >/dev/null
gql_field ".data.logTrainerHours.id" \
  "mutation { logTrainerHours(input: { trainerId: \"$TRAINER_CLARA\", sessionOccurrenceId: \"$OCC_PILATES_3\", hoursWorked: 1.0, notes: \"Thu Pilates wk3\" }) { id } }" \
  >/dev/null
echo "  Clara: 3 approved log entries"

# ============================================================
# T12 — Verification queries (--verify flag)
# ============================================================
if [[ "$VERIFY" == "--verify" ]]; then
  echo ""
  echo "==> T12: Verification queries"

  echo ""
  echo "--- members ---"
  gql '{ members { id firstName lastName currentStatus } }' | jq '.data.members'

  echo ""
  echo "--- trainers ---"
  gql '{ trainers { id firstName lastName } }' | jq '.data.trainers'

  echo ""
  echo "--- sessions ---"
  gql '{ sessions { id name sessionType dayOfWeek } }' | jq '.data.sessions'

  echo ""
  echo "--- outstandingPayments ---"
  # Note: member/subscription nested fields omitted — lazy-load limitation in this query
  gql '{ outstandingPayments { amountDue amountPaid outstanding } }' \
    | jq '.data.outstandingPayments'

  echo ""
  echo "--- overdueSubscriptions (expect Felix) ---"
  gql '{ overdueSubscriptions { member { firstName lastName } paymentStatus dueDate daysOverdue } }' \
    | jq '.data.overdueSubscriptions'

  echo ""
  echo "--- pendingTrainerLogs (expect Ben resubmitted) ---"
  # Note: trainer nested field omitted — lazy-load limitation
  gql '{ pendingTrainerLogs { id hoursWorked status } }' \
    | jq '.data.pendingTrainerLogs'

  echo ""
  echo "--- pendingPaymentReviews (expect empty) ---"
  gql '{ pendingPaymentReviews { id member { firstName } paymentStatus } }' \
    | jq '.data.pendingPaymentReviews'
fi

echo ""
echo "==> Data load complete."
