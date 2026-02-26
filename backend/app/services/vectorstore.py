"""ChromaDB vector store operations."""
import chromadb
from app.config import settings
from app.services.embedding import encode

_client: chromadb.ClientAPI | None = None
COLLECTION_NAME = "messages"


def _get_collection() -> chromadb.Collection:
    global _client
    if _client is None:
        _client = chromadb.PersistentClient(path=settings.chroma_dir)
    return _client.get_or_create_collection(
        name=COLLECTION_NAME, metadata={"hnsw:space": "cosine"}
    )


def add_messages(conversation_id: str, messages) -> None:
    """Embed and store messages into ChromaDB."""
    col = _get_collection()
    ids = [m.id for m in messages]
    texts = [m.content for m in messages]
    metadatas = [
        {
            "conversation_id": conversation_id,
            "sender": m.sender,
            "timestamp": m.timestamp or "",
            "type": "message",
        }
        for m in messages
    ]
    embeddings = encode(texts)
    col.add(ids=ids, embeddings=embeddings, documents=texts, metadatas=metadatas)


def add_summary(conversation_id: str, summary: str, title: str = "", participants: str = "") -> None:
    """Embed conversation summary into ChromaDB for long-term memory retrieval."""
    col = _get_collection()
    doc_id = f"summary-{conversation_id}"
    metadata = {
        "conversation_id": conversation_id,
        "sender": "",
        "timestamp": "",
        "type": "summary",
        "title": title,
        "participants": participants,
    }
    embedding = encode([summary])[0]
    # upsert so re-generating summary updates the vector
    col.upsert(ids=[doc_id], embeddings=[embedding], documents=[summary], metadatas=[metadata])


def search(
    query: str, top_k: int = 10, conversation_id: str | None = None
) -> list[dict]:
    col = _get_collection()
    query_embedding = encode([query])[0]
    where = {"conversation_id": conversation_id} if conversation_id else None
    results = col.query(
        query_embeddings=[query_embedding], n_results=top_k, where=where
    )
    items = []
    for i in range(len(results["ids"][0])):
        items.append(
            {
                "message_id": results["ids"][0][i],
                "content": results["documents"][0][i],
                "metadata": results["metadatas"][0][i],
                "score": 1 - results["distances"][0][i],  # cosine similarity
            }
        )
    return items


def delete_conversation_vectors(conversation_id: str) -> None:
    col = _get_collection()
    col.delete(where={"conversation_id": conversation_id})
