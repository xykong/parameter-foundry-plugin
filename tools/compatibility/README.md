# Compatibility Audit

`audit-parameter-modes.py` performs a read-only aggregate inventory of Jenkins
parameter definitions. It requires an explicit base URL and either a secret
argument or an external, owner-readable-only JSON credential file.

Example:

```bash
python3 tools/compatibility/audit-parameter-modes.py \
  --base-url https://jenkins.example.invalid \
  --auth-file /secure/path/jenkins-credentials.json \
  --auth-key jenkins_oidc_escape_hatch_secret \
  > /tmp/unified-parameters-audit.json
```

The output intentionally omits Job names, option values, credentials, tokens,
and raw Job XML. Exit status is non-zero when discovery or configuration
reading is incomplete.

Local parser tests:

```bash
python3 -m unittest tools/compatibility/test_audit_parameter_modes.py
```
