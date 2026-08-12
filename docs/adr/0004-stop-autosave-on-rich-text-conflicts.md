# Stop autosave on rich-text conflicts

When optimistic locking detects concurrent changes to a Note or Reflection, autosave stops and preserves the user's local edits until they explicitly reload the persisted version or copy their local work. M5 deliberately avoids last-writer-wins and automatic rich-text merging because either could silently lose or corrupt private writing.
