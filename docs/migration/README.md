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

Additional companion adapters cover:

- Active Choices `ChoiceParameter`, `CascadeChoiceParameter`, and formatted
  reference parameters through non-executing Provider/Form skeletons.
- List Git Branches through a governed Git-ref Provider skeleton without
  copying repository URLs or credentials.

Future source adapters must add at least one A, B, C, and D fixture where the
source plugin supports those classifications.
