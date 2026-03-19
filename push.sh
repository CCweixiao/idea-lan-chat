#!/bin/bash
# IDEA LAN Chat 推送脚本
# 使用方法：在本地执行此脚本

set -e

# 设置 Git 用户信息（如果需要）
git config --global user.email "your_email@example.com"
git config --global user.name "Your Name"

# 推送到 GitHub
echo "正在推送到 GitHub..."
git push -u origin main

echo "✅ 推送完成！"
echo "仓库地址: https://github.com/CCweixiao/idea-lan-chat"
