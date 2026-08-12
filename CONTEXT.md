# rotrack Domain Language

rotrack records explicitly tracked activity and private study context without inferring untracked time or duplicating authoritative session facts.

## Tracking

**Time Entry**:
An authoritative interval of explicitly tracked Work or Rot activity. It may be active or completed.
_Avoid_: Session record, timer record

**Session Label**:
Optional plain text that briefly labels a Time Entry.
_Avoid_: Note, rich-text note

## Private Study Context

**Note**:
A private, independent rich-text document that may be contextually attached to one Time Entry. It may later move, detach, become empty, or survive deletion of its attached Time Entry.
_Avoid_: Session Label, Reflection

**Note Draft**:
Local meaningful edits that have not yet become a Note. It captures the active Time Entry or standalone context when meaningful editing begins and never silently changes that context.
_Avoid_: Empty Note, saved draft

**Note Summary**:
A list projection that identifies a Note and provides enough title, preview, attachment, and revision context to select it without carrying the rich-text document.
_Avoid_: Note document, full Note

**Save Conflict**:
Divergence between local edits and a newer persisted revision of the same Note or Reflection. Both versions remain distinct until the user explicitly preserves or discards the local work.
_Avoid_: Autosave error, merge

**Reflection**:
Private rich-text writing identified by its owner and original local-date label, independent of the timezone used to project activity. Once created, it retains that identity even when its content is cleared.
_Avoid_: Note, daily note

**Daily Log**:
A generated completed-activity view of authoritative Time Entry segments and Note references for one local date, plus its optional Reflection. It is not a persisted snapshot.
_Avoid_: Daily record, persisted daily snapshot

**Daily Log Summary**:
A calendar projection of totals, counts, and meaningful Reflection presence for one local date without private detail content.
_Avoid_: Daily Log, daily snapshot
