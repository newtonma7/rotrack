import { NotesWorkspace } from "@/components/notes/NotesWorkspace";

export default async function NotePage({ params }: { params: Promise<{ id: string }> }) {
  const { id } = await params;
  return <NotesWorkspace selectedNoteId={id} />;
}
