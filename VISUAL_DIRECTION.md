# Native v0.1.0 visual direction

Candidate tokens: background #0D1014; surface #171E27; raised #222C38; primary text #E5E8EC; secondary text #A5AFBD; accent #D97745. Validate actual foreground/background contrast before acceptance. Orange is for selected state and primary action, not every icon or section. Use silver-grey hairline borders; avoid decorative metallic gradients.

Primary destinations: Projects, Search, Inbox, Review. Show active project and provider filter persistently. Present records as readable lists; selection opens an evidence detail screen with source version, origin, review status, relationships, and history. Queue rows show metadata only until read approval. Review compares proposed content, destination, sources and conflicts, with per-item accept/edit/reject.

Connection status is visible but diagnostics belong in Settings/Connection. Use at least 48dp touch targets, scalable text, labelled statuses, keyboard/screen-reader navigation, and tested Hebrew/RTL mixed with LTR identifiers. Avoid color-only distinctions and gratuitous animation. Preserve literal identifiers while allowing descriptive text to follow locale direction.

Future browser graph: project/provider filters, bounded expandable neighborhoods, source drawer, truncation indicators, and reduced motion. Graph positions are presentation state, never a second knowledge store. v0.1.0 preserves typed stable evidence references; it does not ship React Flow or a WebView graph.
