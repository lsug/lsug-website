---
name: Speaker Details
about: Add details for a speaker and event
title: Add bio and event for [SPEAKER]
labels: content
assignees: ''

---

Thank you very much for joining us! It would be great if you could fill in some details.

# A note on file formats
The `pm` format is inspired by [pollen markup syntax](https://docs.racket-lang.org/pollen/), a programmable xml-like format for semantic document publishing.  I appreciate that there is no widespread editor support for this yet. If you're an Emacs user, you can have a go with [pollen-mode](https://github.com/zainab-ali/pollen-mode). 

You may have trouble typing the lozenge character ◊. If so, see [pollen's instructions on inserting unicode](https://docs.racket-lang.org/pollen/pollen-command-syntax.html).

# Your profile
 - Take a look at the [bio template](https://github.com/lsug/lsug-website/blob/master/server/src/main/resources/people-template.pm).  
 - Copy the template to [the people directory](https://github.com/lsug/lsug-website/tree/master/server/src/main/resources/people).
 - You must fill in your name and bio. The `photo`, `twitter`, `github` and `blog` fields are optional. 
    Let me know if you'd like any other fields - we can add them in the long term.
 - Photos will make your profile all the more friendly. If you'd like one, store it in [the assets directory](https://github.com/lsug/lsug-website/tree/master/web/assets).

# Your event

It would greatly help contributors if you could give a brief overview of the projects you'd like them to work on, and the main issues they face. If you have a specific label for good first issues (e.g. `good-first-issue`), feel free to link it here.

Please also link a contributor's guide, if you have one prepared, and any setup instructions. We'll encourage attendees to do the setup beforehand.

## Filling in the details

The event details are in [2021-01-28](https://github.com/lsug/lsug-website/blob/master/server/src/main/resources/meetups/2021-01-28.pm).

Add a new `event` tag to the `events` section (unless your event is already here). It must have a `name`, a `time` and a `description`.

You can optionally add `tags`, `slides` and `material` for any other links.  [The Coding with Cats workshop](https://www.lsug.co.uk/events/2020-03-06/0) has an example of how these are displayed.

See the [meetup template](https://github.com/lsug/lsug-website/blob/master/server/src/main/resources/meetup-template.pm) for details on how to structure each of these fields.
