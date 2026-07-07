"""
Kairo -- LLM Brain Module
Utilizes LangGraph for conversation state/flow management and ChatOllama
to run the local LLaMA small language model (SLM) with conversation memory.
"""

from __future__ import annotations

from typing import Annotated, TypedDict
from langchain_core.messages import BaseMessage, SystemMessage
from langchain_ollama import ChatOllama
from langgraph.checkpoint.memory import MemorySaver
from langgraph.graph import START, END, StateGraph
from langgraph.graph.message import add_messages


class State(TypedDict):
    """LangGraph state representation for the conversation."""
    messages: Annotated[list[BaseMessage], add_messages]


class LLMBrain:
    """Conversational AI brain using LangGraph and Ollama."""

    def __init__(self, model_name: str = "llama3.2:1b", temperature: float = 0.7):
        """
        Initialize the LLM brain.

        Args:
            model_name: The name of the model in Ollama.
            temperature: Sampling temperature (lower is more deterministic).
        """
        self.model_name = model_name
        self.temperature = temperature

        print(f"[BRAIN] Initializing ChatOllama with model '{model_name}'...")
        self.llm = ChatOllama(
            model=model_name,
            temperature=temperature,
        )

        # Build the LangGraph StateGraph
        builder = StateGraph(State)
        builder.add_node("chatbot", self._call_model)
        builder.add_edge(START, "chatbot")
        builder.add_edge("chatbot", END)

        # Add memory saver checkpointing for multi-turn conversations
        self.memory = MemorySaver()
        self.graph = builder.compile(checkpointer=self.memory)
        print("[BRAIN] LangGraph conversation pipeline compiled [OK]")

    def _call_model(self, state: State) -> dict:
        """Node function to invoke the LLM with system prompt + chat history."""
        system_prompt = SystemMessage(
            content=(
                "You are Kairo, a helpful, friendly, and concise local AI voice assistant. "
                "Your answers will be spoken out loud using text-to-speech. "
                "Ensure your responses are natural, highly conversational, and brief (1 to 3 sentences) "
                "unless the user specifically asks for more detail."
            )
        )
        # Prepend the system prompt to the messages history when calling the model
        messages_to_send = [system_prompt] + state["messages"]
        response = self.llm.invoke(messages_to_send)
        return {"messages": [response]}

    def query(self, text: str, thread_id: str = "default_session") -> str:
        """
        Send a user prompt to the brain and retrieve the reply.

        Args:
            text: The text prompt from the user.
            thread_id: Thread ID to maintain separate conversation memories.

        Returns:
            The assistant's text response.
        """
        config = {"configurable": {"thread_id": thread_id}}
        input_state = {"messages": [("user", text)]}

        try:
            # Stream or run the graph to completion
            output = self.graph.invoke(input_state, config)
            # The last message is the model's response
            last_msg = output["messages"][-1]
            return last_msg.content.strip()
        except Exception as e:
            print(f"[BRAIN] Error during LLM inference: {e}")
            return "I'm sorry, I encountered an error while processing that."


# -- Quick Self Test -----------------------------------------------------------
if __name__ == "__main__":
    # Test model with simple conversation
    brain = LLMBrain(model_name="llama3.2:1b")
    
    print("\n--- Running Brain Self Test ---")
    q1 = "Hello! Who are you?"
    print(f"You: {q1}")
    a1 = brain.query(q1)
    print(f"Kairo: {a1}\n")
    
    q2 = "What is my name? Oh wait, I didn't tell you. My name is Someshwar."
    print(f"You: {q2}")
    a2 = brain.query(q2)
    print(f"Kairo: {a2}\n")
    
    q3 = "Can you repeat my name?"
    print(f"You: {q3}")
    a3 = brain.query(q3)
    print(f"Kairo: {a3}\n")
