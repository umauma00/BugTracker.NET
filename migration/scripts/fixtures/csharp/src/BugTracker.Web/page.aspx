<%@ Page Language="C#" CodeBehind="page.aspx.cs" Inherits="btnet.page" %>
<!-- #include file = "inc.aspx" -->
<%@ Register Src="~/Controls/Menu.ascx" TagPrefix="uc" TagName="Menu" %>
<script runat="server">
void Page_Load(object s, System.EventArgs e) { if (User.Identity.IsExt()) Response.Write(Util.get("x")); }
</script>
<%= btnet.User.copy() %>
