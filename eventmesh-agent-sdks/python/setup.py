"""Setup for EventMesh Agent SDK for Python."""
from setuptools import setup, find_packages

setup(
    name="eventmesh-agent",
    version="0.1.0",
    description="EventMesh Agent SDK — connect any Python agent to Apache EventMesh A2A Agent Mesh",
    long_description=open("README.md").read() if __import__("os").path.exists("README.md") else "",
    long_description_content_type="text/markdown",
    author="Apache EventMesh Contributors",
    url="https://github.com/apache/eventmesh",
    packages=find_packages(),
    python_requires=">=3.6",
    classifiers=[
        "Development Status :: 3 - Alpha",
        "Intended Audience :: Developers",
        "License :: OSI Approved :: Apache Software License",
        "Programming Language :: Python :: 3",
        "Programming Language :: Python :: 3.6",
        "Programming Language :: Python :: 3.7",
        "Programming Language :: Python :: 3.8",
        "Programming Language :: Python :: 3.9",
        "Programming Language :: Python :: 3.10",
        "Programming Language :: Python :: 3.11",
        "Programming Language :: Python :: 3.12",
        "Topic :: Software Development :: Libraries",
        "Topic :: Scientific/Engineering :: Artificial Intelligence",
    ],
    keywords="eventmesh agent a2a multi-agent mesh",
)
