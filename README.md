# Ledger

A small HTTP service for recording money movements against accounts. You can open an
account, deposit, withdraw, check the balance, and read back the transaction history.
No UI, no database, and everything lives in memory and resets when you stop the app.

## Assumptions
- I have not added a functionality for account deletion (although implemented at the storage level, I have not exposed the functionality), as deleting an 
account means I need to look into where to transfer the existing balance which could make this much more complicated.

- We have not added the user scope and the whole ledger works on the account scope. This is based on the assumption 
that for this specific task, we don't really need to have users as an entity. But in theory a single user can have 
multiple accounts. 

- All storage is in memory, so killing the server would mean all entries vanish from the store. We can always add an 
implementation for storage interface to migrate to the actual disk.

- Have bundled exceptions into limited required exception classes but in the production system there would be many more
well defined exceptions with much better http code association.

- Consistency in a multi threaded environment comes from two things. ConcurrentHashMap's atomic operations
(`putIfAbsent`, `computeIfAbsent`) handle the idempotency keys and reject duplicate transaction ids, and a lock
on each account keeps the overdraft check and the balance update together so they cannot interleave. A production
system and DB would have much higher level of isolation and reconciliation checks. Adding them here would have
made the code very complicated.

- I have used 3 concurrent hashmaps : two in the storage layer for the transactions and for the accounts with their
balance, and one in the service layer mapping idempotency keys to the transaction they produced. I have 
tried to keep this as simple and optimised as possible and specifically made for the requirements in this exercise. 
In a system where all the functionality is added, we might need either more complex data structures or more data 
structures to maintain and store all the required relations just like in a normalised DB.

- Have made following assumptions and as well added the  explicit checks to ensure we have proper controlled validations for the inputs :
  - One cannot deposit money with granularity less than a cent. In other words, a cent is the smallest denomination.
  - The maximum amount that can be deposited or withdrawn in a transaction has been limited to less than 10^15, as we don't want an erroneous input.
  - We have kept the description length on any transaction to be at most 256 chars.
  - Max length of the idempotency key is 64 chars and it can't be null or blank.
  - Amount in any transaction can't be less than or equal to 0.
  
- No record can exist without an account which means anyone who wants to deposit or withdraw an amount would have 
to call the account creation API first to get an accountId.

- Idempotency key maps to an account : If an idempotency key is repeated and I find that the whole transaction matches 
with the previous one, I return a 200 with the details of the previous transaction that has already been performed 
(a 201 would claim something was created, when nothing was). If there is a match found but the content is different 
from the previous transaction, we throw an exception.

- Surrounding whitespace on an idempotency key is ignored, so " dep-1" and "dep-1" are the same key. A stray space is
invisible and usually accidental, and without trimming a retry carrying one would be applied a second time. Case is
deliberately left alone, as keys are opaque strings and "DEP-1" is a different key on purpose.


## Running it
Needs Java 21. Nothing else to install.

```bash
./mvnw spring-boot:run
```

It starts on port 8080.

`./mvnw` is the Maven wrapper, committed deliberately so you don't need Maven installed — it
downloads the exact version this was built with (3.9.14) on first run and caches it. A JDK is
the only prerequisite.

## Running the tests

```bash
./mvnw test
```

Tests covering the storage, service and HTTP layers.

## The API

Base path is `/api/ledger`.

| Method | Path | What it does |
|---|---|---|
| `POST` | `/accounts` | Opens an account. Returns the new id as plain text. |
| `POST` | `/deposit` | Records money in. |
| `POST` | `/withdraw` | Records money out. Refused if the balance is too low. |
| `GET` | `/accounts/{accountId}/balance` | Current balance. |
| `GET` | `/accounts/{accountId}/transactions` | Full history, oldest first. |
| `GET` | `/ledger-entry/{transactionId}` | A single entry. |

Both `POST` bodies look the same (for withdraw and deposit):

```json
{
  "idempotencyKey": "any-key",
  "description": "salary",
  "accountId": "the-account-id",
  "amount": 1000.00
}
```

`idempotencyKey` is required. More on why below.\
`accountId` : To get this, you need to first call the `/accounts` API which would return the accountId 

## Try it

Open an account and keep the id handy:

```bash
B=http://localhost:8080/api/ledger
ACC=$(curl -s -X POST $B/accounts); echo $ACC
```

Deposit, then withdraw:

```bash
curl -s -X POST $B/deposit -H 'Content-Type: application/json' \
  -d "{\"idempotencyKey\":\"dep-1\",\"description\":\"salary\",\"accountId\":\"$ACC\",\"amount\":1000.00}"

curl -s -X POST $B/withdraw -H 'Content-Type: application/json' \
  -d "{\"idempotencyKey\":\"wdr-1\",\"description\":\"rent\",\"accountId\":\"$ACC\",\"amount\":250.50}"
```

Each returns the entry that was created:

```json
{
  "accountId": "a8550b5c-38fe-4969-9135-ee244f7aa759",
  "transactionId": "b0970f93-7344-4257-a2b5-89203ebecdda",
  "entryType": "DEPOSIT",
  "description": "salary",
  "amount": 1000.00,
  "createdAt": "2026-10-28T18:42:29.232107Z"
}
```

Balance and history:

```bash
curl -s $B/accounts/$ACC/balance        # 749.50
curl -s $B/accounts/$ACC/transactions   # both entries, oldest first
```

Try to overdraw and you get a 422:

```bash
curl -s -X POST $B/withdraw -H 'Content-Type: application/json' \
  -d "{\"idempotencyKey\":\"wdr-2\",\"description\":\"too much\",\"accountId\":\"$ACC\",\"amount\":999999.00}"
```

```json
{"detail":"Insufficient funds exception : a8550b5c-...","status":422,"title":"Unprocessable Content"}
```

### Retrying safely

Send the first deposit again, with the same `dep-1` key:

```bash
curl -s -X POST $B/deposit -H 'Content-Type: application/json' \
  -d "{\"idempotencyKey\":\"dep-1\",\"description\":\"salary\",\"accountId\":\"$ACC\",\"amount\":1000.00}"
```

You get back the *same* `transactionId`, and the balance doesn't move. That's the point:
if your connection drops and you never see the response, you can safely retry instead of
guessing whether the money landed.

The status is `200 OK` rather than the `201 Created` you got the first time, because this
request created nothing — it handed back what already existed.

<b>NOTE :</b>  If you reuse that key for something different, it's rejected:

```bash
curl -s -X POST $B/deposit -H 'Content-Type: application/json' \
  -d "{\"idempotencyKey\":\"dep-1\",\"description\":\"salary\",\"accountId\":\"$ACC\",\"amount\":5000.00}"
```

```json
{"detail":"Idempotency key reused with a different request : dep-1","status":400}
```

Otherwise you'd ask to deposit 5000 and get handed a receipt for 1000.

## Error codes used

| Code | When |
|---|---|
| `200` | Read succeeded, or a replayed request returned its original transaction |
| `201` | A new account or ledger entry was created |
| `400` | Bad input — malformed JSON, missing field, negative amount, key reused with different content |
| `404` | Account or transaction doesn't exist |
| `422` | Request is fine but the balance won't allow it |
| `500` | Our fault |

<b>NOTE :</b> 
The split between 400 and 422 is deliberate. A 400 means re-sending the same request will
never work. A 422 means the request is fine and might succeed later — deposit some money
and that same withdrawal goes through.

## Decisions and trade-offs

**Everything is in memory.** Two maps: one of transactions keyed by id, one of accounts
holding a running balance. Restarting loses everything. The brief asked for this.

**Balances are stored, not recalculated.** Reading a balance is a map lookup rather than a
sum over every past entry. Faster, but it means the balance and the entries could in
principle disagree — which is why they're only ever updated together, under one lock.

**Locking is per account.** Each account has its own monitor, and a write holds it across
the overdraft check, the entry write and the balance update. Two people moving money on
different accounts never wait for each other. Two on the same account take turns, so you
can't overdraw by racing.

**Idempotency keys are mandatory.** A key plus an account identifies one intended
operation. Send the same one twice and the second is treated as a retry. This means
**your client has to generate a fresh key per operation** — if you hardcode one, your
second genuine deposit will be silently swallowed as a duplicate. A UUID minted when the
user taps the button is the usual approach.

**A rejected request doesn't consume its key.** If a withdrawal is refused for insufficient
funds, you can retry with the same key once there's money. Only successful writes are
remembered.

**Keys are never expired.** The map of used keys grows forever. A real service would drop
them after a day or so. Left out to keep things simple.

**Amounts are capped at two decimal places** and just under 10^15. `BigDecimal` is
arbitrary-precision, so without limits you can deposit a thousand-digit number or a
fraction of a cent.

**Ids are random UUIDs and aren't checked for uniqueness up front.** The storage layer
rejects a duplicate on insert, same as a database primary key would. A collision would be
a server fault, so it returns 500 rather than blaming the caller. This was done due to rare probability of collision 
with UUID generation.

**Validation happens twice.** Once on the way in via annotations, once inside the service.
The annotations only run for HTTP requests, and the service limits are the ones that would
corrupt stored data, so they're checked wherever the call comes from.

**Closing an account isn't exposed.** The storage layer can delete one and the service has
a method for it, but there's no endpoint — deciding what should happen to a non-zero
balance, or to the history, is a bigger question than this exercise needs. Transactions
deliberately outlive their account: a closed account's entries are still readable by id.

**Known gap:** deleting an account isn't synchronised against writes, so a deposit landing
at the exact moment of deletion could be recorded against an account that's just gone.
Unreachable as of today, since nothing exposes deletion for now.

**Initialisation and bean creation:** used spring-boot annotations and capabilities to keep the custom code 
to minimum and still achieve what we want. Have kept the addition of annotations to the minimum to ensure the code is still readable.

**Unique ids:** account ids and transaction ids are both random UUIDs (122 random bits). Generate a trillion of them
and the chance of any collision at all is roughly 1 in 10 trillion. Given that, we have not added retries if a
generated UUID collides, and just throw an exception instead.

**Transaction history:** is returned in an ordered manner with the oldest one first.


## Design Layout

```
controller/   HTTP endpoints and the exception-to-status mapping
service/      business rules, idempotency
dao/          in-memory storage and the locking
model/        request and entry types
exception/    one class per failure case
```

The storage layer sits behind a `LedgerStorage` interface, so swapping in a database means
writing one new implementation and nothing else. Idempotency deliberately lives at the service layer,
a key identifies a client request, not a stored row in the DB, so it shouldn't be reimplemented per
backend.
