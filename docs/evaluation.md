# Evaluating ClaimPilot on your own policies

The public evaluation set (`backend/src/test/resources/eval/cases.json`) uses fictional documents so it
can live in a public repository. Real policies cannot, but you can measure ClaimPilot on them locally:
anything under `sample-docs/private/` is ignored by git.

## 1. Add the documents and the questions

```
sample-docs/private/eval/
  car-policy.pdf
  car-policy.json
```

`car-policy.json` lists the documents (by file name, next to the JSON file), the questions with the
answer you expect, and the key facts that should be read from each document:

```json
{
  "documents": {
    "car": {"file": "car-policy.pdf", "kind": "policy"}
  },
  "questions": [
    {"policy": "car", "question": "What is my collision deductible?",
     "status": "ANSWERED", "page": 3, "mustContain": ["500"]},
    {"policy": "car", "question": "Is a rental car covered after an accident?",
     "status": "ANSWERED", "page": 5, "mustContain": ["rental"]},
    {"policy": "car", "question": "Does the policy cover my dental care?",
     "status": "NOT_IN_POLICY", "page": null, "mustContain": []}
  ],
  "facts": [
    {"document": "car", "key": "POLICY_NUMBER", "value": "the number printed on your policy"}
  ]
}
```

- `status`: `ANSWERED` (the policy answers it), `UNCLEAR` (the policy leaves it open, for example "may be
  considered"), or `NOT_IN_POLICY`.
- `page`: the page that holds the answer, or `null`.
- `mustContain`: words the answer must include; `"a|b"` accepts either.
- `facts` keys: `INSURER_NAME`, `INSURER_PHONE`, `POLICY_NUMBER`, `CERTIFICATE_NUMBER`,
  `PLAN_MEMBER_NAME`, `PLAN_SPONSOR` for policies; `SERVICE_DATE`, `AMOUNT_CHARGED`, `PROVIDER_NAME`,
  `RECEIPT_NUMBER` for receipts (`"kind": "receipt"`).

Write the expected answers from the document before running the evaluation, not from ClaimPilot's
answers, so the check stays honest.

## 2. Run it

With Ollama running (`qwen3:8b` and `bge-m3`):

```
cd backend
./mvnw test -Peval
```

`target/eval-report.md` then has one row per set (the public sample set and each of your files) and the
details of every question. The report stays on your computer.
