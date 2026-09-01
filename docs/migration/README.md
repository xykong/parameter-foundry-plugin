# Migration Design Fixtures

These synthetic fixtures exercise business-requirement migration without
copying production Job XML or credentials.

Each fixture contains:

- source intent;
- adapter classification;
- explicit semantic decisions;
- expected Unified Parameters definition;
- verification cases.

Current fixture:

- [Extended Choice static multiple checkbox](./extended-choice-static-multiple.yaml)
- [Extended Choice static order-sensitive decision](./extended-choice-static-order-sensitive.yaml)
- [Active Choices ChoiceParameter](./active-choices-choice-c.yaml)
- [Active Choices CascadeChoiceParameter](./active-choices-cascade-c.yaml)
- [Active Choices DynamicReferenceParameter](./active-choices-reference-c.yaml)
- [List Git Branches PT_BRANCH](./list-git-branches-pt-branch-c.yaml)
- [List Git Branches ORG_BRANCH](./list-git-branches-organization-branch-c.yaml)
- [Additional source boundaries](./additional-source-boundaries.yaml)

Additional companion adapters cover:

- Active Choices `ChoiceParameter`, `CascadeChoiceParameter`, and formatted
  reference parameters through non-executing Provider/Form skeletons.
- List Git Branches through a governed Git-ref Provider skeleton without
  copying repository URLs or credentials.

Future source adapters must add at least one A, B, C, and D fixture where the
source plugin supports those classifications.

`python3 tools/migration/validate-fixtures.py` checks that every researched
source family has fixture coverage and that fixture IDs are unique.
