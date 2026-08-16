/** Version-one rich-text document accepted by the Notes API. */
export type RichTextMark =
  | { type: "bold" }
  | { type: "italic" }
  | { type: "link"; attrs: { href: string } };

export interface RichTextText {
  type: "text";
  text: string;
  marks?: RichTextMark[];
}

export interface RichTextParagraph {
  type: "paragraph";
  content?: RichTextText[];
}

export interface RichTextHeading {
  type: "heading";
  attrs: { level: 2 | 3 };
  content?: RichTextText[];
}

export interface RichTextListItem {
  type: "listItem";
  content: [RichTextParagraph, ...RichTextListItemChild[]];
}

export interface RichTextTaskItem {
  type: "taskItem";
  attrs: { checked: boolean };
  content: [RichTextParagraph, ...RichTextTaskItemChild[]];
}

export type RichTextList = RichTextBulletList | RichTextOrderedList;
export type RichTextListItemChild = RichTextParagraph | RichTextList | RichTextTaskList | RichTextBlockquote;
export type RichTextTaskItemChild = RichTextParagraph | RichTextList | RichTextTaskList | RichTextBlockquote;

export interface RichTextBulletList {
  type: "bulletList";
  content: [RichTextListItem, ...RichTextListItem[]];
}

export interface RichTextOrderedList {
  type: "orderedList";
  attrs?: { start?: number };
  content: [RichTextListItem, ...RichTextListItem[]];
}

export interface RichTextTaskList {
  type: "taskList";
  content: [RichTextTaskItem, ...RichTextTaskItem[]];
}

export interface RichTextBlockquote {
  type: "blockquote";
  content: [RichTextBlockquoteChild, ...RichTextBlockquoteChild[]];
}

export type RichTextBlockquoteChild = RichTextParagraph | RichTextList | RichTextTaskList | RichTextBlockquote;
export type RichTextBlock = RichTextParagraph | RichTextHeading | RichTextList | RichTextTaskList | RichTextBlockquote;

export interface RichTextDocument {
  schemaVersion: 1;
  document: {
    type: "doc";
    content: RichTextBlock[];
  };
}

export interface NoteSummary {
  id: string;
  title: string | null;
  preview: string;
  timeEntryId: string | null;
  version: number;
  createdAt: string;
  updatedAt: string;
}

export interface Note extends NoteSummary {
  contentJson: RichTextDocument;
  contentText: string;
  contentSchemaVersion: 1;
}

export interface CreateNoteRequest {
  title: string | null;
  contentJson: RichTextDocument;
  timeEntryId: string | null;
}

export interface UpdateNoteRequest extends CreateNoteRequest {
  expectedVersion: number;
}

export type NoteAttachmentFilter = "ATTACHED" | "STANDALONE";

export interface NoteListFilters {
  cursor?: string;
  attachment?: NoteAttachmentFilter;
  timeEntryId?: string;
}

export interface NotePage {
  notes: NoteSummary[];
  nextCursor: string | null;
}
